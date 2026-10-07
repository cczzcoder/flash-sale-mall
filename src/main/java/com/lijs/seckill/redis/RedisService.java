package com.lijs.seckill.redis;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@DependsOn("jedisPool") // 确保 jedisPoolConfig 先加载
@Service
public class RedisService {

    private final Logger logger = LoggerFactory.getLogger(RedisService.class);

    @Autowired
    private JedisPool jedisPool;

    /**
     * 统一获取/归还 Jedis 连接：所有 Redis 操作经此执行，
     * 消除每个方法里重复的 getResource + try/finally close 样板。
     * 只负责归还连接，不吞异常。
     */
    private <T> T execute(Function<Jedis, T> action) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            return action.apply(jedis);
        } finally {
            close(jedis);
        }
    }

    /** 无返回值的 Redis 操作。 */
    private void executeVoid(Consumer<Jedis> action) {
        execute(jedis -> {
            action.accept(jedis);
            return null;
        });
    }

    /**
     * 获取单个对象
     */
    public <T> T get(KeyPrefix prefix, String key, Class<T> data) {
        logger.debug("get key:{}", key);
        return execute(jedis -> {
            // 生成真正的key  className+":"+prefix;  BasePrefix:id1
            String realKey = prefix.getPrefix() + key;
            logger.debug("get realKey:{}", realKey);
            String value = jedis.get(realKey);
            logger.debug("get value:{}", value);
            // 将String转换为Bean
            return stringToBean(value, data);
        });
    }

    /**
     * redis删除对象
     */
    public boolean delete(KeyPrefix prefix, String key) {
        return execute(jedis -> {
            String realKey = prefix.getPrefix() + key;
            // 删除成功，返回大于0
            return jedis.del(realKey) > 0;
        });
    }

    /**
     * 设置redis对象
     */
    public <T> boolean set(KeyPrefix prefix, String key, T value) {
        logger.debug("set key:{}", key);
        return execute(jedis -> {
            String realKey = prefix.getPrefix() + key;
            logger.debug("set realKey:{}", realKey);
            String s = beanToString(value);
            if (s == null || s.isEmpty()) {
                return false;
            }
            int seconds = prefix.expireSeconds();
            if (seconds <= 0) {
                // 有效期：小于0代表不过期
                jedis.set(realKey, s);
            } else {
                // 设置过期时间
                jedis.setex(realKey, seconds, s);
            }
            return true;
        });
    }

    /**
     * Only initialize a cache value when the key does not exist.
     * Used for stock warm-up so an application restart cannot overwrite an
     * already-consumed Redis reservation with the database snapshot.
     */
    public <T> boolean setIfAbsent(KeyPrefix prefix, String key, T value) {
        return execute(jedis -> {
            String realKey = prefix.getPrefix() + key;
            String serialized = beanToString(value);
            if (serialized == null || serialized.isEmpty()) {
                return false;
            }
            long created = jedis.setnx(realKey, serialized);
            if (created == 1 && prefix.expireSeconds() > 0) {
                jedis.expire(realKey, prefix.expireSeconds());
            }
            return created == 1;
        });
    }

    /**
     * 减少值
     */
    public Long decr(KeyPrefix prefix, String key) {
        return execute(jedis -> jedis.decr(prefix.getPrefix() + key));
    }

    /**
     * 增加值
     */
    public void incr(KeyPrefix prefix, String key) {
        executeVoid(jedis -> jedis.incr(prefix.getPrefix() + key));
    }

    /**
     * 判断 key 是否存在
     */
    public boolean existsKey(KeyPrefix prefix, String key) {
        return execute(jedis -> jedis.exists(prefix.getPrefix() + key));
    }

    /**
     * 将字符串转换为Bean对象
     * <p>
     * parseInt()返回的是基本类型int 而valueOf()返回的是包装类Integer
     * Integer是可以使用对象方法的  而int类型就不能和Object类型进行互相转换 。
     * int a=Integer.parseInt(s);
     * Integer b=Integer.valueOf(s);
     */
    public static <T> T stringToBean(String s, Class<T> clazz) {
        if (s == null || s.isEmpty() || clazz == null) {
            return null;
        }
        if (clazz == int.class || clazz == Integer.class) {
            return ((T) Integer.valueOf(s));
        } else if (clazz == String.class) {
            return (T) s;
        } else if (clazz == long.class || clazz == Long.class) {
            return (T) Long.valueOf(s);
        } else {
            JSONObject json = JSON.parseObject(s);
            return JSON.toJavaObject(json, clazz);
        }
    }

    /**
     * 将Bean对象转换为字符串类型
     */
    public static <T> String beanToString(T value) {
        if (value == null) {
            return null;
        }
        Class<?> clazz = value.getClass();
        if (clazz == Integer.class) {
            return "" + value;
        } else if (clazz == String.class) {
            return "" + value;
        } else if (clazz == Long.class) {
            return "" + value;
        } else {
            return JSON.toJSONString(value);
        }
    }

    /**
     * 关闭连接
     */
    private void close(Jedis jedis) {
        if (jedis != null) {
            jedis.close();
        }
    }

    public <T> boolean set(String key, T value) {
        return execute(jedis -> {
            // 将T类型转换为String类型
            String s = beanToString(value);
            if (s == null) {
                return false;
            }
            jedis.set(key, s);
            return true;
        });
    }

    public <T> T get(String key, Class<T> data) {
        return execute(jedis -> stringToBean(jedis.get(key), data));
    }

    /**
     * 预减库存（秒杀库存扣减）。
     *
     * <p>使用 Lua 脚本将"GET 判断 + DECR"合并为 Redis 原子操作，消除原来
     * DECR / INCR 两步之间 JVM 崩溃导致库存永久为负的中间态风险。
     *
     * @param prefix key前缀
     * @param key    商品ID对应的库存Key
     * @return 扣减成功返回 true，库存不足返回 false
     */
    public boolean preDecrStock(KeyPrefix prefix, String key) {
        return execute(jedis -> {
            String realKey = prefix.getPrefix() + key;

            // Lua 脚本：GET + 判断 + DECR 原子执行，避免两步操作之间崩溃留下负值
            // 返回值：DECR 后的库存值（≥0 表示扣减成功）；-1 表示库存不足（未执行 DECR）
            String luaScript =
                "local s = tonumber(redis.call('GET', KEYS[1])) " +
                "if s and s > 0 then " +
                "    return redis.call('DECR', KEYS[1]) " +
                "else " +
                "    return -1 " +
                "end";

            long stock = (Long) jedis.eval(luaScript, 1, realKey);

            if (stock < 0) {
                logger.warn("库存不足，预扣失败, key: {}", realKey);
                return false;
            }
            logger.debug("库存预减后数量: {}, key: {}", stock, realKey);
            return true;
        });
    }

    /**
     * 回滚库存（如果秒杀失败）
     * @param prefix key前缀
     * @param key 商品ID对应的库存Key
     */
    public void rollbackStock(KeyPrefix prefix, String key) {
        increaseBy(prefix, key, 1);
    }

    /** 按 reservationId 幂等回补一次库存，标记和加库存通过 Lua 原子完成。 */
    public boolean rollbackStockOnce(KeyPrefix stockPrefix, String stockKey, String reservationId) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            rollbackStock(stockPrefix, stockKey);
            return true;
        }
        return execute(jedis -> {
            String stockRedisKey = stockPrefix.getPrefix() + stockKey;
            String reservationRedisKey = GoodsKey.getSeckillReservation.getPrefix() + reservationId;
            int ttl = GoodsKey.getSeckillReservation.expireSeconds();
            String luaScript =
                    "if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end " +
                    "redis.call('INCRBY', KEYS[1], ARGV[1]) " +
                    "redis.call('SETEX', KEYS[2], ARGV[2], ARGV[3]) " +
                    "return 1";
            Object result = jedis.eval(luaScript, 2, stockRedisKey, reservationRedisKey,
                    "1", String.valueOf(ttl), "RELEASED");
            return result != null && ((Long) result) == 1L;
        });
    }

    /** 标记 reservation 已完成 DB 下单，供 ACK 失败后的重复投递识别。 */
    public boolean markReservationCommitted(String reservationId) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            return false;
        }
        return execute(jedis -> {
            String reservationRedisKey = GoodsKey.getSeckillReservation.getPrefix() + reservationId;
            int ttl = GoodsKey.getSeckillReservation.expireSeconds();
            String luaScript =
                    "local current = redis.call('GET', KEYS[1]) " +
                    "if not current then " +
                    "  redis.call('SETEX', KEYS[1], ARGV[1], ARGV[2]) return 1 " +
                    "elseif current == ARGV[2] then return 1 " +
                    "else return 0 end";
            Object result = jedis.eval(luaScript, 1, reservationRedisKey,
                    String.valueOf(ttl), "COMMITTED");
            return result != null && ((Long) result) == 1L;
        });
    }

    /** 判断 reservation 是否已经完成 DB 下单。 */
    public boolean isReservationCommitted(String reservationId) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            return false;
        }
        String state = get(GoodsKey.getSeckillReservation, reservationId, String.class);
        return "COMMITTED".equals(state);
    }

    /** 原子增加指定数量，用于订单关闭后按 goodsCount 回补 Redis 库存。 */
    public long increaseBy(KeyPrefix prefix, String key, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        return execute(jedis -> {
            String realKey = prefix.getPrefix() + key;
            long stock = jedis.incrBy(realKey, count);
            logger.info("库存回滚成功, key: {}, count: {}, stock: {}", realKey, count, stock);
            return stock;
        });
    }

    /**
     * 库存对账：仅当 Redis 当前值等于 expect 时才写入 newValue。
     *
     * <p>比较与写入通过 Lua 原子执行。若期间有秒杀请求扣减了库存，
     * 当前值不再等于 expect，写入被放弃，本轮对账跳过——宁可少修一次，
     * 也不能用一个已经过期的快照覆盖掉真实扣减。
     *
     * @return true 表示成功修正，false 表示值已变化（有并发扣减）或 key 不存在
     */
    public boolean compareAndSetStock(KeyPrefix prefix, String key, long expect, long newValue) {
        return execute(jedis -> {
            String realKey = prefix.getPrefix() + key;

            // KEYS[1]=库存key, ARGV[1]=期望值, ARGV[2]=修正值
            // key 不存在时 GET 返回 false，tonumber(false) 为 nil，比较不成立，返回 0
            String luaScript =
                "local s = redis.call('GET', KEYS[1]) " +
                "if s and tonumber(s) == tonumber(ARGV[1]) then " +
                "    redis.call('SET', KEYS[1], ARGV[2]) " +
                "    return 1 " +
                "else " +
                "    return 0 " +
                "end";

            Object r = jedis.eval(luaScript, 1, realKey,
                    String.valueOf(expect), String.valueOf(newValue));
            return r != null && ((Long) r) == 1L;
        });
    }

    // -------------------------------------------------------------------------
    // Redis List 操作（AI 对话历史）
    // -------------------------------------------------------------------------

    /**
     * 在 List 尾部追加一条消息，同时修剪为最近 maxSize 条，并刷新 TTL。
     * 三步操作通过 Lua 脚本原子执行，避免崩溃时留下超长 List 或 TTL 丢失。
     *
     * @param prefix   key 前缀
     * @param key      sessionId
     * @param value    已序列化的消息字符串（JSON）
     * @param maxSize  保留的最大消息条数
     * @param ttlSecs  key 过期时间（秒）
     */
    public void listAppend(KeyPrefix prefix, String key, String value, int maxSize, int ttlSecs) {
        executeVoid(jedis -> {
            String realKey = prefix.getPrefix() + key;
            // 原子：RPUSH + LTRIM（保留末尾 maxSize 条）+ EXPIRE
            String lua =
                "redis.call('RPUSH', KEYS[1], ARGV[1]) " +
                "redis.call('LTRIM', KEYS[1], -tonumber(ARGV[2]), -1) " +
                "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3])) " +
                "return 1";
            jedis.eval(lua, 1, realKey, value, String.valueOf(maxSize), String.valueOf(ttlSecs));
        });
    }

    /**
     * 读取 List 中的所有消息（LRANGE 0 -1）。
     *
     * @return 消息字符串列表，key 不存在时返回空 List
     */
    public List<String> listRange(KeyPrefix prefix, String key) {
        return execute(jedis -> jedis.lrange(prefix.getPrefix() + key, 0, -1));
    }

    /**
     * 从 List 尾部弹出并删除最后一条消息（RPOP），用于异常时回滚。
     *
     * @return 被移除的消息字符串；List 为空或 key 不存在时返回 null
     */
    public String listRpop(KeyPrefix prefix, String key) {
        return execute(jedis -> jedis.rpop(prefix.getPrefix() + key));
    }
}

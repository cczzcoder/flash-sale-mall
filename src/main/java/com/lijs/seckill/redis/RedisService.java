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

@DependsOn("jedisPool") // 确保 jedisPoolConfig 先加载
@Service
public class RedisService {

    private final Logger logger = LoggerFactory.getLogger(RedisService.class);

    @Autowired
    private JedisPool jedisPool;

    /**
     * 获取单个对象
     */
    public <T> T get(KeyPrefix prefix, String key, Class<T> data) {
        logger.info("get key:{}", key);
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            // 生成真正的key  className+":"+prefix;  BasePrefix:id1
            String realKey = prefix.getPrefix() + key;
            logger.info("get realKey:{}", realKey);
            String value = jedis.get(realKey);
            logger.info("get value:{}", value);
            // 将String转换为Bean
            return stringToBean(value, data);
        } finally {
            close(jedis);
        }
    }

    /**
     * redis删除对象
     */
    public boolean delete(KeyPrefix prefix, String key) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String realKey = prefix.getPrefix() + key;
            long ret = jedis.del(realKey);
            // 删除成功，返回大于0
            return ret > 0;
        } finally {
            close(jedis);
        }
    }

    /**
     * 设置redis对象
     */
    public <T> boolean set(KeyPrefix prefix, String key, T value) {
        logger.info("set key:{}", key);
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String realKey = prefix.getPrefix() + key;
            logger.info("set realKey:{}", realKey);
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
        } finally {
            close(jedis);
        }
    }

    /**
     * 减少值
     */
    public <T> Long decr(KeyPrefix prefix, String key) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String realKey = prefix.getPrefix() + key;
            return jedis.decr(realKey);
        } finally {
            close(jedis);
        }
    }

    /**
     * 增加值
     */
    public <T> void incr(KeyPrefix prefix, String key) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String realKey = prefix.getPrefix() + key;
            jedis.incr(realKey);
        } finally {
            close(jedis);
        }
    }

    /**
     * 检查key是否存在
     */
    public <T> boolean exitsKey(KeyPrefix prefix, String key) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String realKey = prefix.getPrefix() + key;
            return jedis.exists(realKey);
        } finally {
            close(jedis);
        }
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
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            // 将T类型转换为String类型
            String s = beanToString(value);
            if (s == null) {
                return false;
            }
            jedis.set(key, s);
            return true;
        } finally {
            close(jedis);
        }
    }

    public <T> T get(String key, Class<T> data) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String value = jedis.get(key);
            return stringToBean(value, data);
        } finally {
            close(jedis);
        }
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
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
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
            logger.info("库存预减后数量: {}, key: {}", stock, realKey);
            return true;
        } finally {
            close(jedis);
        }
    }

    /**
     * 回滚库存（如果秒杀失败）
     * @param prefix key前缀
     * @param key 商品ID对应的库存Key
     */
    public void rollbackStock(KeyPrefix prefix, String key) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String realKey = prefix.getPrefix() + key;
            jedis.incr(realKey);
            logger.info("库存回滚成功, key: {}", realKey);
        } finally {
            close(jedis);
        }
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
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String realKey = prefix.getPrefix() + key;
            // 原子：RPUSH + LTRIM（保留末尾 maxSize 条）+ EXPIRE
            String lua =
                "redis.call('RPUSH', KEYS[1], ARGV[1]) " +
                "redis.call('LTRIM', KEYS[1], -tonumber(ARGV[2]), -1) " +
                "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3])) " +
                "return 1";
            jedis.eval(lua, 1, realKey, value, String.valueOf(maxSize), String.valueOf(ttlSecs));
        } finally {
            close(jedis);
        }
    }

    /**
     * 读取 List 中的所有消息（LRANGE 0 -1）。
     *
     * @return 消息字符串列表，key 不存在时返回空 List
     */
    public List<String> listRange(KeyPrefix prefix, String key) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String realKey = prefix.getPrefix() + key;
            return jedis.lrange(realKey, 0, -1);
        } finally {
            close(jedis);
        }
    }

    /**
     * 从 List 尾部弹出并删除最后一条消息（RPOP），用于异常时回滚。
     *
     * @return 被移除的消息字符串；List 为空或 key 不存在时返回 null
     */
    public String listRpop(KeyPrefix prefix, String key) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String realKey = prefix.getPrefix() + key;
            return jedis.rpop(realKey);
        } finally {
            close(jedis);
        }
    }
}

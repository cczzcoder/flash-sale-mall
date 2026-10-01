package com.lijs.seckill.redis;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Jedis 连接池工厂，读取 {@link RedisConfig} 配置并创建 {@link JedisPool} Bean。
 *
 * <p>连接池参数说明：
 * <ul>
 *   <li>maxTotal  — 最大连接数，超出时新请求阻塞等待直到 maxWait 超时</li>
 *   <li>maxIdle   — 最大空闲连接数，超出的空闲连接会被回收</li>
 *   <li>minIdle   — 最小空闲连接数，保持预热的连接数量，避免冷启动延迟</li>
 *   <li>maxWait   — 获取连接的最大等待毫秒数</li>
 * </ul>
 *
 * <p>密码判断：Redis 未设置密码时不传递 password 参数，避免 Jedis 抛出认证错误。
 *
 * <p>{@link RedisService} 通过 {@code @DependsOn("jedisPool")} 确保本 Bean 先行初始化。
 */
@Configuration
public class RedisPoolFactory {

    @Autowired
    private RedisConfig redisConfig;

    /**
     * 创建并注册 JedisPool Bean。
     * timeout 配置单位为秒，此处乘以 1000 转为毫秒后传给 Jedis。
     */
    @Bean
    public JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxIdle(redisConfig.getPoolMaxldle());
        poolConfig.setMinIdle(redisConfig.getPoolMinldle());
        poolConfig.setMaxTotal(redisConfig.getPoolMaxTotal());
        poolConfig.setMaxWaitMillis(redisConfig.getPoolMaxWait() * 1000L);

        if (StringUtils.isEmpty(redisConfig.getPassword())) {
            // Redis 无密码时，使用不含 password 参数的构造方法
            return new JedisPool(poolConfig, redisConfig.getHost(), redisConfig.getPort(),
                    redisConfig.getTimeout() * 1000);
        }
        // Redis 有密码时，传入密码和 db 编号（0 = 默认库）
        return new JedisPool(poolConfig, redisConfig.getHost(), redisConfig.getPort(),
                redisConfig.getTimeout() * 1000, redisConfig.getPassword(), 0);
    }
}

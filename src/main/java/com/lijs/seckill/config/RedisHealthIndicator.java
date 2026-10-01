package com.lijs.seckill.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/** 自定义 Jedis 健康检查，不在响应中输出地址、口令等敏感配置。 */
@Component("redis")
public class RedisHealthIndicator implements HealthIndicator {

    private final JedisPool jedisPool;

    public RedisHealthIndicator(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public Health health() {
        try (Jedis jedis = jedisPool.getResource()) {
            return "PONG".equalsIgnoreCase(jedis.ping()) ? Health.up().build()
                    : Health.down().withDetail("reason", "unexpected ping response").build();
        } catch (Exception e) {
            return Health.down().withDetail("reason", e.getClass().getSimpleName()).build();
        }
    }
}

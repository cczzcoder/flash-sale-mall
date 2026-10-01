package com.lijs.seckill.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Redis 连接配置属性，自动绑定 application.yml 中 {@code redis.*} 前缀的配置项。
 *
 * <p>对应配置示例（application.yml）：
 * <pre>
 * redis:
 *   host: 127.0.0.1
 *   port: 6379
 *   timeout: 3          # 连接超时（秒，代码中乘以 1000 转为毫秒）
 *   password:           # 无密码留空
 *   pool-max-total: 10  # 连接池最大连接数
 *   pool-max-ldle: 10   # 最大空闲连接数
 *   pool-min-ldle: 1    # 最小空闲连接数
 *   pool-max-wait: 3    # 获取连接的最大等待时间（秒）
 * </pre>
 *
 * <p>属性值由 {@link RedisPoolFactory} 读取后用于初始化 {@link redis.clients.jedis.JedisPool}。
 */
@Component
@ConfigurationProperties(prefix = "redis")
public class RedisConfig {

    /** Redis 服务器地址 */
    private String host;
    /** Redis 端口，默认 6379 */
    private int port;
    /** 连接超时（秒），RedisPoolFactory 中乘以 1000 转为毫秒 */
    private int timeout;
    /** Redis 认证密码，未设置密码时留空 */
    private String password;
    /** 连接池最大连接数（maxTotal） */
    private int poolMaxTotal;
    /** 连接池最大空闲连接数（maxIdle） */
    private int poolMaxldle;
    /** 连接池最小空闲连接数（minIdle） */
    private int poolMinldle;
    /** 从连接池获取连接的最大等待时间（秒） */
    private int poolMaxWait;

    public String getHost()                      { return host; }
    public void   setHost(String host)           { this.host = host; }
    public int    getPort()                      { return port; }
    public void   setPort(int port)              { this.port = port; }
    public int    getTimeout()                   { return timeout; }
    public void   setTimeout(int timeout)        { this.timeout = timeout; }
    public String getPassword()                  { return password; }
    public void   setPassword(String password)   { this.password = password; }
    public int    getPoolMaxTotal()              { return poolMaxTotal; }
    public void   setPoolMaxTotal(int v)         { this.poolMaxTotal = v; }
    public int    getPoolMaxldle()               { return poolMaxldle; }
    public void   setPoolMaxldle(int v)          { this.poolMaxldle = v; }
    public int    getPoolMaxWait()               { return poolMaxWait; }
    public void   setPoolMaxWait(int v)          { this.poolMaxWait = v; }
    public int    getPoolMinldle()               { return poolMinldle; }
    public void   setPoolMinldle(int v)          { this.poolMinldle = v; }
}

package com.lijs.seckill.redis;

/**
 * 接口访问频率限制相关的 Redis Key 前缀。
 *
 * <p>限流计数存储格式：{@code AccessKey:access<uri_userId>} → 访问次数（Integer）。
 * TTL 由 {@link #expire(int)} 动态指定，等于 @AccessLimit 注解中的 seconds 参数，
 * 即"时间窗口到期时 key 自动删除，计数自动重置"。
 *
 * <p>使用方式：
 * <ul>
 *   <li>{@link #access} — 固定 5s 窗口的静态实例，供 {@link com.lijs.seckill.controller.SeckillController} 内联限流使用</li>
 *   <li>{@link #expire(int)} — 根据 @AccessLimit 注解动态创建，供 {@link com.lijs.seckill.access.AccessInterceptor} 使用</li>
 * </ul>
 */
public class AccessKey extends BasePrefix {

    public AccessKey(int expireSeconds, String prefix) {
        super(expireSeconds, prefix);
    }

    /**
     * 固定 5s 窗口的访问计数 key，供 SeckillController 内联限流直接使用。
     * Redis key 格式：{@code AccessKey:access<uri_userId>}，TTL = 5s
     */
    public static AccessKey access = new AccessKey(5, "access");

    /**
     * 根据 @AccessLimit 注解动态创建限流 key，TTL = 注解中指定的 seconds。
     * AccessInterceptor 在每次请求拦截时调用此方法获取对应窗口的 key 前缀。
     *
     * @param expireSeconds @AccessLimit 注解中 seconds() 的值
     * @return 携带指定 TTL 的 AccessKey 实例
     */
    public static AccessKey expire(int expireSeconds) {
        return new AccessKey(expireSeconds, "access");
    }
}

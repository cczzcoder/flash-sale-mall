package com.lijs.seckill.redis;

/**
 * 秒杀用户相关的 Redis Key 前缀定义。
 *
 * <p>包含两类 key：
 * <ul>
 *   <li>{@link #token} — 分布式 Session 存储，key = {@code SeckillUserKey:tk<uuid>}，
 *       value = SeckillUser JSON，TTL = 2 天（48 小时）。
 *       用户登录后生成，每次请求命中时自动续期（滑动窗口）。</li>
 *   <li>{@link #getById} — 用户对象缓存，key = {@code SeckillUserKey:id<userId>}，
 *       永不过期，避免高并发时频繁查库。</li>
 * </ul>
 */
public class SeckillUserKey extends BasePrefix {

    /** token 有效期：2 天（3600s × 24 × 2） */
    public static final int TOKEN_EXPIRE = 3600 * 24 * 2;

    public SeckillUserKey(int expireSeconds, String prefix) {
        super(expireSeconds, prefix);
    }

    /**
     * 分布式 Session 的 token key，TTL = 2 天。
     * Redis 存储：{@code SeckillUserKey:tk<token>} → SeckillUser JSON
     */
    public static SeckillUserKey token = new SeckillUserKey(TOKEN_EXPIRE, "tk");

    /**
     * 按用户 ID 缓存用户对象，永不过期。
     * Redis 存储：{@code SeckillUserKey:id<userId>} → SeckillUser JSON
     */
    public static SeckillUserKey getById = new SeckillUserKey(0, "id");
}

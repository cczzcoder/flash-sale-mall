package com.lijs.seckill.redis;

/**
 * 普通用户（非秒杀用户）相关的 Redis Key 前缀。
 *
 * <p>此类预留用于缓存 {@link com.lijs.seckill.domain.User} 对象，
 * 与 {@link SeckillUserKey}（缓存 SeckillUser）相互独立，避免 key 冲突。
 *
 * <p>存储格式：
 * <ul>
 *   <li>{@code UserKey:id<userId>}   — 按 ID 缓存用户对象</li>
 *   <li>{@code UserKey:name<name>}   — 按用户名缓存用户对象</li>
 * </ul>
 * TTL = 0（永不过期），适合用户基础信息变更不频繁的场景。
 */
public class UserKey extends BasePrefix {

    public UserKey(String prefix) {
        super(prefix); // expireSeconds = 0，永不过期
    }

    /** 按用户 ID 缓存，key = {@code UserKey:id<userId>} */
    public static UserKey getById   = new UserKey("id");

    /** 按用户名缓存，key = {@code UserKey:name<username>} */
    public static UserKey getByName = new UserKey("name");
}

package com.lijs.seckill.redis;

/**
 * Redis Key 前缀抽象基类，实现 {@link KeyPrefix} 接口，提供统一的前缀生成规则。
 *
 * <p>key 命名规范：{@code 子类类名:prefix + 业务key}，例如：
 * <ul>
 *   <li>{@code GoodsKey:gl}（商品列表页缓存）</li>
 *   <li>{@code SeckillUserKey:tk<token>}（用户 token 缓存）</li>
 *   <li>{@code OrderKey:ms_uid_gid<userId_goodsId>}（秒杀订单缓存）</li>
 * </ul>
 * 类名前缀保证了不同业务模块的 key 天然隔离，不会互相覆盖。
 */
public abstract class BasePrefix implements KeyPrefix {

    /** 缓存过期时间（秒），0 表示永不过期 */
    private final int expireSeconds;
    /** 业务标识短码，与类名拼接构成完整前缀 */
    private final String prefix;

    /**
     * 永不过期的 key（expireSeconds = 0）。
     * 适用于用户对象缓存、库存数量等不需要自动清除的场景。
     */
    public BasePrefix(String prefix) {
        this.expireSeconds = 0;
        this.prefix = prefix;
    }

    /**
     * 带过期时间的 key。
     *
     * @param expireSeconds 过期时间（秒），0 表示永不过期
     * @param prefix        业务标识短码
     */
    public BasePrefix(int expireSeconds, String prefix) {
        this.expireSeconds = expireSeconds;
        this.prefix = prefix;
    }

    /** 返回过期时间，0 表示永不过期 */
    @Override
    public int expireSeconds() {
        return expireSeconds;
    }

    /**
     * 生成完整前缀：{@code 子类类名:prefix}。
     * 例如 {@code GoodsKey:gl}、{@code SeckillUserKey:tk}。
     */
    @Override
    public String getPrefix() {
        String className = getClass().getSimpleName();
        return className + ":" + prefix;
    }
}

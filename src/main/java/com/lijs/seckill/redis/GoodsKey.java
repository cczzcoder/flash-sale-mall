package com.lijs.seckill.redis;

/**
 * 商品相关的 Redis Key 前缀定义。
 *
 * <p>包含三类 key：
 * <ul>
 *   <li>{@link #getGoodsList}       — 商品列表页 HTML 缓存，TTL = 60s。
 *       key = {@code GoodsKey:gl}（全局唯一，所有用户共享同一份缓存）。</li>
 *   <li>{@link #getGoodsDetail}     — 商品详情页 HTML 缓存，TTL = 60s，按 goodsId 隔离。
 *       key = {@code GoodsKey:gd<goodsId>}。</li>
 *   <li>{@link #getSeckillGoodsStock} — 秒杀商品库存计数，永不过期。
 *       key = {@code GoodsKey:gs<goodsId>}，value = 剩余库存数量（Integer）。
 *       系统启动时从 DB 预热写入，秒杀时通过 Lua 脚本原子扣减。</li>
 * </ul>
 */
public class GoodsKey extends BasePrefix {

    public GoodsKey(int expireSeconds, String prefix) {
        super(expireSeconds, prefix);
    }

    /**
     * 商品列表页缓存，TTL = 60s（缓存时间短，保证列表数据的相对实时性）。
     * 用于 {@link com.lijs.seckill.controller.GoodsController#goodsListWithCache}。
     */
    public static GoodsKey getGoodsList = new GoodsKey(60, "gl");

    /**
     * 商品详情页缓存，TTL = 60s，每个商品独立缓存。
     * 用于 {@link com.lijs.seckill.controller.GoodsController#goodsDetailCache}。
     */
    public static GoodsKey getGoodsDetail = new GoodsKey(60, "gd");

    /**
     * 秒杀商品库存，永不过期（expireSeconds = 0）。
     * 启动时预热：{@link com.lijs.seckill.controller.SeckillController#afterPropertiesSet}
     * 原子扣减：{@link com.lijs.seckill.redis.RedisService#preDecrStock}（Lua 脚本）
     * 回滚：{@link com.lijs.seckill.redis.RedisService#rollbackStock}
     */
    public static GoodsKey getSeckillGoodsStock = new GoodsKey(0, "gs");

    /** 秒杀预扣库存 reservation 的最终状态，保存一周用于跨进程重试幂等。 */
    public static GoodsKey getSeckillReservation = new GoodsKey(604800, "gsr");
}

package com.lijs.seckill.redis;

/**
 * 秒杀相关的 Redis Key 前缀定义。
 *
 * <p>包含三类 key：
 * <ul>
 *   <li>{@link #isGoodsOver}    — 商品是否售罄标记，key = {@code SeckillKey:go<goodsId>}，
 *       value = true，永不过期。DB 库存扣减失败时写入，秒杀请求可据此快速返回售罄，减少 DB 压力。</li>
 *   <li>{@link #getSeckillPath} — 防刷的一次性秒杀 Path，key = {@code SeckillKey:mp<userId_goodsId>}，
 *       TTL = 60s。前端先调 /getPath 取到随机串，再用该串拼秒杀 URL；服务端验证后立即删除，防重放。</li>
 *   <li>{@link #getSeckillVerifyCode} — 图片验证码计算结果，key = {@code SeckillKey:vc<userId_goodsId>}，
 *       TTL = 300s。用于校验用户填写的算式答案，校验后立即删除，防暴力枚举。</li>
 * </ul>
 */
public class SeckillKey extends BasePrefix {

    public SeckillKey(int expireSeconds, String prefix) {
        super(expireSeconds, prefix);
    }

    /**
     * 商品售罄标记，永不过期（expireSeconds = 0）。
     * DB 库存不足时写入，后续请求先查此 key，命中则直接返回售罄，避免继续打 DB。
     */
    public static SeckillKey isGoodsOver = new SeckillKey(0, "go");

    /**
     * 一次性秒杀 Path，TTL = 60s。
     * 生成：{@link com.lijs.seckill.service.SeckillService#createSeckillPath}
     * 校验：{@link com.lijs.seckill.service.SeckillService#checkPath}（验证通过后立即删除）
     */
    public static SeckillKey getSeckillPath = new SeckillKey(60, "mp");

    /**
     * 图片验证码答案，TTL = 300s。
     * 生成：{@link com.lijs.seckill.service.VerifyCodeService#createSeckillVerifyCode}
     * 校验：{@link com.lijs.seckill.service.VerifyCodeService#checkVCode}（校验后立即删除）
     */
    public static SeckillKey getSeckillVerifyCode = new SeckillKey(300, "vc");

    /** 秒杀活动开始/结束时间，商品编辑时主动删除并由请求或启动流程重建。 */
    public static SeckillKey getSeckillWindow = new SeckillKey(0, "sw");
}

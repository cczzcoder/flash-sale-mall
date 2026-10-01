package com.lijs.seckill.redis;

/**
 * 秒杀订单相关的 Redis Key 前缀。
 *
 * <p>目前只有一个 key，用于缓存"用户 + 商品"维度的秒杀订单，
 * 供重复下单校验和轮询结果查询使用，避免高频请求直接打到数据库。
 *
 * <p>存储格式：
 * {@code OrderKey:ms_uid_gid<userId_goodsId>} → SeckillOrder JSON
 *
 * <p>写入时机：{@link com.lijs.seckill.service.OrderService#createCacheOrder} 事务提交后写入。
 * 读取时机：
 * <ul>
 *   <li>重复下单校验：{@link com.lijs.seckill.controller.SeckillController#seckillWithCacheAndMQ}</li>
 *   <li>轮询秒杀结果：{@link com.lijs.seckill.service.SeckillService#getSeckillResult}</li>
 * </ul>
 *
 * <p>TTL = 0（永不过期），确保订单信息在 Redis 中长期有效。
 * 数据一致性兜底：数据库 seckill_order 表有唯一索引 u_uid_gid，
 * 即使缓存写失败，DB 唯一索引也能防止重复下单。
 */
public class OrderKey extends BasePrefix {

    public OrderKey(String prefix) {
        super(prefix);  // 调用 BasePrefix(String)，expireSeconds = 0（永不过期）
    }

    /**
     * 按"用户ID + 商品ID"缓存秒杀订单，永不过期。
     * Redis key 示例：{@code OrderKey:ms_uid_gid123_456}（userId=123，goodsId=456）
     */
    public static OrderKey getSeckillOrderByUidAndGid = new OrderKey("ms_uid_gid");
}

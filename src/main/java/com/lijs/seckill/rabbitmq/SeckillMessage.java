package com.lijs.seckill.rabbitmq;

import com.lijs.seckill.domain.SeckillUser;

/**
 * 秒杀 MQ 消息体，封装投递到 RabbitMQ 的秒杀请求数据。
 *
 * <p>流程：
 * <ol>
 *   <li>{@link com.lijs.seckill.controller.SeckillController#seckillWithCacheAndMQ}
 *       在 Redis 预扣库存成功后，将用户和商品 ID 封装成此对象，
 *       序列化为 JSON 字符串投入 {@link MQConfig#SECKILL} 队列。</li>
 *   <li>{@link MQReceiver#receiveSeckill} 消费消息后，反序列化为本对象，
 *       再执行数据库层面的库存扣减和订单写入。</li>
 * </ol>
 *
 * <p>消息内容尽量精简，只携带后续处理必要的最小字段（用户对象 + 商品ID），
 * 减小序列化体积，降低网络和存储开销。
 */
public class SeckillMessage {

    /** 发起秒杀的用户信息，消费端据此写入订单的 userId */
    private SeckillUser user;

    /** 被秒杀的商品 ID，消费端据此查库存和写订单 */
    private long goodsId;

    /** 用户在下单时选择的地址；为空时由订单服务选择默认地址。 */
    private Long deliveryAddrId;

    /** 每次 Redis 预扣对应的唯一请求标识，用于发布确认与日志关联 */
    private String reservationId;

    public SeckillUser getUser()              { return user; }
    public void        setUser(SeckillUser u) { this.user = u; }
    public long        getGoodsId()           { return goodsId; }
    public void        setGoodsId(long id)    { this.goodsId = id; }
    public Long getDeliveryAddrId() { return deliveryAddrId; }
    public void setDeliveryAddrId(Long deliveryAddrId) { this.deliveryAddrId = deliveryAddrId; }
    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
}

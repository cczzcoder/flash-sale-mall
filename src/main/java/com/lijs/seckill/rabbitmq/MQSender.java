package com.lijs.seckill.rabbitmq;

import com.lijs.seckill.redis.GoodsKey;
import com.lijs.seckill.redis.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * MQ 生产者。
 *
 * <p>可靠性设计：
 * <ol>
 *   <li>模板重试（spring.rabbitmq.template.retry.*）：TCP 闪断时最多重试3次，
 *       重试耗尽仍失败则抛出 AmqpException，由调用方（SeckillController）捕获并回滚 Redis。</li>
 *   <li>Publisher Confirm（spring.rabbitmq.publisher-confirm-type=correlated）：
 *       Broker 接受 TCP 但未落盘时，通过 ConfirmCallback 异步回调补偿 Redis 库存。</li>
 * </ol>
 * 两者组合覆盖"TCP 失败"与"Broker 落盘失败"两种消息丢失场景。
 */
@Service
public class MQSender {

    private final Logger logger = LoggerFactory.getLogger(MQSender.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisService redisService;

    /**
     * 注册 Publisher-Confirm 回调。
     * 当 Broker 返回 NACK（落盘失败）时，从 CorrelationData 中取出商品 ID，
     * 执行幂等回补，将 Redis 预扣的1个库存补回。
     */
    @PostConstruct
    public void init() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack && correlationData != null) {
                String correlationId = correlationData.getId();
                String[] parts = correlationId == null ? new String[0] : correlationId.split(":", 2);
                String goodsId = parts.length == 0 ? "" : parts[0];
                String reservationId = parts.length < 2 ? null : parts[1];
                if (goodsId.isEmpty()) {
                    logger.error("MQ Broker NACK 但 correlationId 无法定位商品，跳过自动回补, correlationId={}", correlationId);
                    return;
                }
                boolean restored = redisService.rollbackStockOnce(
                        GoodsKey.getSeckillGoodsStock, goodsId, reservationId);
                logger.error("MQ Broker NACK，Redis 库存回滚={}, goodsId={}, cause={}",
                        restored, goodsId, cause);
            }
        });
    }

    /**
     * 发送秒杀消息。
     * 使用默认 Exchange（""），routing key = 队列名，保持与原有队列绑定方式一致。
     * 携带 CorrelationData（商品ID）以便 ConfirmCallback 回调时定位需要回滚的库存 key。
     *
     * @throws org.springframework.amqp.AmqpException TCP 重试耗尽后抛出，调用方须 catch 并回滚 Redis
     */
    @SuppressWarnings("null") // String.valueOf(long) 对基本类型不会返回 null，JDT 误报
    public void sendSeckillMessage(SeckillMessage message) {
        String msg = RedisService.beanToString(message);
        if (msg == null) {
            logger.error("序列化失败，消息为 null, goodsId={}", message.getGoodsId());
            throw new IllegalArgumentException("SeckillMessage 序列化结果为 null");
        }
        logger.info("send seckill message, goodsId={}", message.getGoodsId());
        String correlationId = message.getGoodsId() + ":" + message.getReservationId();
        CorrelationData correlationData = new CorrelationData(correlationId);
        // 默认 Exchange（""）+ 队列名作为 routing key，等价于原来的 convertAndSend(queueName, msg)
        rabbitTemplate.convertAndSend("", MQConfig.SECKILL, (Object) msg, correlationData);
    }
}

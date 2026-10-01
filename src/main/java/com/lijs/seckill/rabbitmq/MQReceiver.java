package com.lijs.seckill.rabbitmq;

import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.GoodsKey;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.service.GoodsService;
import com.lijs.seckill.service.SeckillService;
import com.lijs.seckill.vo.GoodsVo;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * MQ 消费者。
 * 手动 ACK 策略：
 *  - 正常消费（含库存不足）→ basicAck
 *  - 唯一索引冲突（重复下单）→ 回滚 Redis 预扣后 basicAck，视为幂等成功
 *  - 其他 DB 写入异常 → basicNack(requeue=false) 进死信队列
 *  - ACK 异常 → 不回补库存，等待 RabbitMQ 重投；reservation 状态保证重投幂等
 */
@Service
public class MQReceiver {

    @Autowired
    private GoodsService goodsService;
    @Autowired
    private SeckillService seckillService;
    @Autowired
    private RedisService redisService;

    private final Logger logger = LoggerFactory.getLogger(MQReceiver.class);

    @RabbitListener(queues = MQConfig.SECKILL)
    public void receiveSeckill(String message,
                               Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        logger.info("receive seckill message: {}", message);
        SeckillMessage mm = RedisService.stringToBean(message, SeckillMessage.class);
        SeckillUser user = mm.getUser();
        long goodsId = mm.getGoodsId();

        boolean dbCommitted = false;
        try {
            GoodsVo goodsVo = goodsService.getGoodsVoByGoodsId(goodsId);

            // 1.事务：DB 扣库存 + 写订单。
            //   不再预判库存/预查重复订单：扣减 SQL 的受影响行数本身就是权威判定，
            //   重复下单由 seckill_order 唯一索引 u_uid_gid 兜底，省两次热路径查询。
            OrderInfo result = seckillService.seckillWithCache(user, goodsVo, mm.getDeliveryAddrId());
            if (result == null) {
                // reduceStock 返回 false（库存已耗尽），补偿 Redis 库存
                rollbackReservation(mm);
                logger.warn("DB 扣减失败，Redis 库存已回滚 goodsId={}", goodsId);
            } else {
                dbCommitted = true;
                // ACK 前记录成功状态，ACK 失败重投时不能误回补库存。
                if (mm.getReservationId() != null && !redisService.markReservationCommitted(mm.getReservationId())) {
                    throw new IllegalStateException("reservation 已被标记为释放，拒绝覆盖 goodsId=" + goodsId);
                }
            }

        } catch (DuplicateKeyException e) {
            // 唯一索引冲突 = 该用户已下过单，事务已回滚故 DB 库存未扣，补偿 Redis 后视为幂等成功
            if (!redisService.isReservationCommitted(mm.getReservationId())) {
                rollbackReservation(mm);
            }
            logger.info("重复下单，幂等丢弃 userId={} goodsId={}", user.getId(), goodsId);

        } catch (Exception e) {
            logger.error("秒杀消费异常，消息转入死信队列 goodsId={}: {}", goodsId, e.getMessage(), e);
            // 只有 DB 未提交时才回补；提交后发生的 Redis 异常不能再增加库存。
            if (!dbCommitted) {
                rollbackReservation(mm);
            }
            // requeue=false → 消息进入死信队列，不无限重投
            try {
                channel.basicNack(tag, false, false);
            } catch (IOException nackError) {
                logger.error("秒杀消息 basicNack 失败 goodsId={}", goodsId, nackError);
            }
            return;
        }

        // ACK 失败只会让 RabbitMQ 重投，不能进入库存补偿分支。
        try {
            channel.basicAck(tag, false);
        } catch (IOException ackError) {
            logger.error("秒杀消息 basicAck 失败，等待 RabbitMQ 重投 goodsId={}, reservationId={}",
                    goodsId, mm.getReservationId(), ackError);
        }
    }

    private void rollbackReservation(SeckillMessage message) {
        redisService.rollbackStockOnce(GoodsKey.getSeckillGoodsStock,
                "" + message.getGoodsId(), message.getReservationId());
    }
}

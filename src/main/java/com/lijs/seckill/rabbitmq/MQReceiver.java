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

        try {
            GoodsVo goodsVo = goodsService.getGoodsVoByGoodsId(goodsId);

            // 1.事务：DB 扣库存 + 写订单。
            //   不再预判库存/预查重复订单：扣减 SQL 的受影响行数本身就是权威判定，
            //   重复下单由 seckill_order 唯一索引 u_uid_gid 兜底，省两次热路径查询。
            OrderInfo result = seckillService.seckillWithCache(user, goodsVo);
            if (result == null) {
                // reduceStock 返回 false（库存已耗尽），补偿 Redis 库存
                redisService.rollbackStock(GoodsKey.getSeckillGoodsStock, "" + goodsId);
                logger.warn("DB 扣减失败，Redis 库存已回滚 goodsId={}", goodsId);
            }
            channel.basicAck(tag, false);

        } catch (DuplicateKeyException e) {
            // 唯一索引冲突 = 该用户已下过单，事务已回滚故 DB 库存未扣，补偿 Redis 后视为幂等成功
            redisService.rollbackStock(GoodsKey.getSeckillGoodsStock, "" + goodsId);
            logger.info("重复下单，幂等丢弃 userId={} goodsId={}", user.getId(), goodsId);
            channel.basicAck(tag, false);

        } catch (Exception e) {
            logger.error("秒杀消费异常，消息转入死信队列 goodsId={}: {}", goodsId, e.getMessage(), e);
            // 补偿 Redis 库存（事务已回滚，DB 未扣减，但 Redis 已预扣）
            redisService.rollbackStock(GoodsKey.getSeckillGoodsStock, "" + goodsId);
            // requeue=false → 消息进入死信队列，不无限重投
            channel.basicNack(tag, false, false);
        }
    }
}


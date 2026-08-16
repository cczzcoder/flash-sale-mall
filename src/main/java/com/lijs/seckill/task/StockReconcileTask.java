package com.lijs.seckill.task;

import com.lijs.seckill.dao.GoodsDao;
import com.lijs.seckill.domain.SeckillGoods;
import com.lijs.seckill.rabbitmq.MQConfig;
import com.lijs.seckill.redis.GoodsKey;
import com.lijs.seckill.redis.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 与 DB 库存对账任务。
 *
 * <p>修复的问题：秒杀链路先扣 Redis 库存再投递 MQ，若两步之间进程崩溃 /
 * Redis 命令成功但响应丢失，Redis 库存会永久少扣，且没有任何补偿路径。
 * 这类泄漏不影响正确性（不会超卖），但会让商品提前显示为"已售罄"。
 *
 * <p>对账的前提是分清两种 Redis &lt; DB：
 * <ul>
 *   <li><b>正常在途</b>：请求已扣 Redis、消息尚在队列中未消费，DB 还没扣。
 *       此时差值会随消费自然收敛，绝不能修正。</li>
 *   <li><b>真实泄漏</b>：队列已排空且无消费者在处理，差值仍然存在。</li>
 * </ul>
 * 因此只在<b>队列深度为 0 且无 unacked 消息</b>时才对账，从根上避免误判在途请求。
 *
 * <p>写回使用 CAS（{@link RedisService#compareAndSetStock}）：若读取快照后
 * 恰好有新的秒杀请求扣减了库存，CAS 失败，本轮跳过，等下一轮再修。
 */
@Component
public class StockReconcileTask {

    private static final Logger logger = LoggerFactory.getLogger(StockReconcileTask.class);

    /** 需连续观测到队列为空的轮数，达到后才认定无在途消息 */
    private static final int REQUIRED_DRAINED_ROUNDS = 2;

    /** 连续观测到队列为空的轮数。仅由单线程的调度任务读写，无需同步 */
    private int consecutiveDrained;

    @Autowired
    private GoodsDao goodsDao;
    @Autowired
    private RedisService redisService;
    @Autowired
    private AmqpAdmin amqpAdmin;

    /**
     * 每 5 分钟对账一次。fixedDelay 保证上一轮执行完才计时下一轮，避免任务堆叠。
     */
    @Scheduled(fixedDelayString = "${seckill.reconcile.interval-ms:300000}",
               initialDelayString = "${seckill.reconcile.initial-delay-ms:60000}")
    public void reconcile() {
        if (!isQueueDrained()) {
            logger.debug("秒杀队列仍有在途消息，跳过本轮库存对账");
            return;
        }

        List<SeckillGoods> goodsList = goodsDao.listStockForReconcile();
        if (goodsList == null || goodsList.isEmpty()) {
            return;
        }

        for (SeckillGoods goods : goodsList) {
            try {
                reconcileOne(goods);
            } catch (Exception e) {
                // 单个商品对账失败不影响其余商品
                logger.error("库存对账异常 goodsId={}", goods.getGoodsId(), e);
            }
        }
    }

    private void reconcileOne(SeckillGoods goods) {
        Long goodsId = goods.getGoodsId();
        Integer dbStock = goods.getStockCount();
        if (goodsId == null || dbStock == null) {
            return;
        }
        String key = "" + goodsId;

        Integer redisStock = redisService.get(GoodsKey.getSeckillGoodsStock, key, Integer.class);
        if (redisStock == null) {
            // key 缺失（Redis 重启且未重新预热）：直接按 DB 重建，避免秒杀请求全部被拒
            redisService.set(GoodsKey.getSeckillGoodsStock, key, dbStock);
            logger.warn("Redis 库存 key 缺失，已按 DB 重建 goodsId={} stock={}", goodsId, dbStock);
            return;
        }

        if (redisStock.intValue() == dbStock.intValue()) {
            return;
        }

        if (redisStock > dbStock) {
            // Redis 比 DB 多：可能被外部误写，继续放行会超卖。只告警不自动改，
            // 因为无法排除"DB 刚被人工补货但 Redis 是对的"这类情况，需人工确认。
            logger.error("库存异常：Redis({}) > DB({})，存在超卖风险，请人工核查 goodsId={}",
                    redisStock, dbStock, goodsId);
            return;
        }

        // Redis < DB 且队列已排空 → 判定为预扣泄漏，按 DB 修正
        boolean fixed = redisService.compareAndSetStock(
                GoodsKey.getSeckillGoodsStock, key, redisStock, dbStock);
        if (fixed) {
            logger.warn("库存泄漏已修正 goodsId={} redis={} -> db={}", goodsId, redisStock, dbStock);
        } else {
            logger.info("对账期间库存发生变化，跳过本轮 goodsId={}", goodsId);
        }
    }

    /**
     * 队列是否已排空。
     *
     * <p>QueueInformation 只暴露 ready 消息数，拿不到 unacked 数，因此单次
     * "ready == 0" 并不能证明没有在途消息（可能正被消费者处理中）。这里要求
     * <b>连续两轮</b>观测到 ready == 0 才认定排空：两轮间隔一个对账周期，
     * 远大于单条消息的处理耗时，足以让上一轮的在途消息完成消费并扣减 DB。
     *
     * <p>查询失败时返回 false 并重置计数（保守跳过），宁可不修也不误修。
     */
    private boolean isQueueDrained() {
        try {
            QueueInformation info = amqpAdmin.getQueueInfo(MQConfig.SECKILL);
            if (info == null) {
                logger.warn("未获取到队列信息，跳过本轮对账 queue={}", MQConfig.SECKILL);
                consecutiveDrained = 0;
                return false;
            }
            if (info.getMessageCount() > 0) {
                consecutiveDrained = 0;
                return false;
            }
            consecutiveDrained++;
            return consecutiveDrained >= REQUIRED_DRAINED_ROUNDS;
        } catch (Exception e) {
            logger.warn("查询队列深度失败，跳过本轮对账: {}", e.getMessage());
            consecutiveDrained = 0;
            return false;
        }
    }
}

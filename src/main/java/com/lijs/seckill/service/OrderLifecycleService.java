package com.lijs.seckill.service;

import com.lijs.seckill.dao.OrderDao;
import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.redis.GoodsKey;
import com.lijs.seckill.redis.OrderKey;
import com.lijs.seckill.redis.RedisService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.List;

/** 支付与超时关单之外的订单生命周期编排。 */
@Service
public class OrderLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(OrderLifecycleService.class);

    private final OrderDao orderDao;
    private final GoodsService goodsService;
    private final RedisService redisService;
    private final SeckillService seckillService;
    private final MeterRegistry meterRegistry;

    public OrderLifecycleService(OrderDao orderDao, GoodsService goodsService,
                                 RedisService redisService, SeckillService seckillService,
                                 MeterRegistry meterRegistry) {
        this.orderDao = orderDao;
        this.goodsService = goodsService;
        this.redisService = redisService;
        this.seckillService = seckillService;
        this.meterRegistry = meterRegistry;
    }

    public List<OrderInfo> findExpiredUnpaid(Date deadline, int limit) {
        return orderDao.selectExpiredUnpaid(deadline, limit);
    }

    @Transactional
    public boolean cancelUnpaidOrder(long orderId, long userId) {
        OrderInfo order = orderDao.getOrderByOrderId(orderId);
        if (order == null || order.getUserId() == null || order.getUserId() != userId) {
            return false;
        }
        if (orderDao.cancelIfUnpaid(orderId, userId) != 1) {
            return false;
        }
        int count = order.getGoodsCount() == null ? 1 : order.getGoodsCount();
        goodsService.increaseStock(order.getGoodsId(), count);
        orderDao.deleteSeckillOrderByOrderId(orderId);
        afterCommit(order, count);
        return true;
    }

    @Transactional
    public boolean shipPaidOrder(long orderId, String shippingCompany, String trackingNumber) {
        if (shippingCompany == null || shippingCompany.trim().isEmpty()
                || trackingNumber == null || trackingNumber.trim().isEmpty()
                || shippingCompany.length() > 32 || trackingNumber.length() > 64) {
            return false;
        }
        return orderDao.markShippedIfPaid(orderId, shippingCompany.trim(), trackingNumber.trim()) == 1;
    }

    @Transactional
    public boolean receiveShippedOrder(long orderId, long userId) {
        return orderDao.markReceivedIfShipped(orderId, userId) == 1;
    }

    @Transactional
    public boolean completeReceivedOrder(long orderId, long userId) {
        return orderDao.markCompletedIfReceived(orderId, userId) == 1;
    }

    @Transactional
    public boolean requestRefund(long orderId, long userId, String reason) {
        if (reason == null || reason.trim().isEmpty() || reason.trim().length() > 255) {
            return false;
        }
        return orderDao.requestRefundIfEligible(orderId, userId, reason.trim()) == 1;
    }

    @Transactional
    public boolean approveRefund(long orderId) {
        return orderDao.markRefundedIfRequested(orderId) == 1;
    }

    /**
     * 条件关单、DB 库存恢复和秒杀映射删除处于同一事务；只有状态更新成功才会回补。
     */
    @Transactional
    public boolean closeExpiredOrder(OrderInfo candidate, Date deadline) {
        if (orderDao.closeIfUnpaidAndExpired(candidate.getId(), deadline) != 1) {
            Counter.builder("order.timeout.close.total").tag("outcome", "skipped")
                    .register(meterRegistry).increment();
            return false;
        }
        int count = candidate.getGoodsCount() == null ? 1 : candidate.getGoodsCount();
        goodsService.increaseStock(candidate.getGoodsId(), count);
        orderDao.deleteSeckillOrderByOrderId(candidate.getId());
        Counter.builder("order.timeout.close.total").tag("outcome", "closed")
                .register(meterRegistry).increment();
        afterCommit(candidate, count);
        return true;
    }

    private void afterCommit(OrderInfo order, int count) {
        Runnable restoreCache = () -> {
            String orderKey = order.getUserId() + "_" + order.getGoodsId();
            try {
                redisService.delete(OrderKey.getSeckillOrderByUidAndGid, orderKey);
                redisService.increaseBy(GoodsKey.getSeckillGoodsStock, "" + order.getGoodsId(), count);
                seckillService.clearGoodsOver(order.getGoodsId());
            } catch (Exception e) {
                Counter.builder("order.timeout.close.total").tag("outcome", "redis_restore_error")
                        .register(meterRegistry).increment();
                // DB 已提交，不能抛回事务；库存对账会修复 Redis < DB。
                logger.error("关单后 Redis 回补失败 orderId={} goodsId={}",
                        order.getId(), order.getGoodsId(), e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            restoreCache.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                restoreCache.run();
            }
        });
    }
}

package com.lijs.seckill.task;

import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.service.OrderLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/** 定时关闭超时未支付订单，状态条件更新保证与支付回调只有一个胜者。 */
@Component
public class OrderTimeoutCloseTask {

    private static final Logger logger = LoggerFactory.getLogger(OrderTimeoutCloseTask.class);

    private final OrderLifecycleService lifecycleService;

    @Value("${order.close.unpaid-timeout-ms:900000}")
    private long unpaidTimeoutMs;

    @Value("${order.close.batch-size:100}")
    private int batchSize;

    public OrderTimeoutCloseTask(OrderLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(fixedDelayString = "${order.close.interval-ms:60000}",
            initialDelayString = "${order.close.initial-delay-ms:60000}")
    public void closeExpiredOrders() {
        Date deadline = new Date(System.currentTimeMillis() - unpaidTimeoutMs);
        List<OrderInfo> candidates = lifecycleService.findExpiredUnpaid(deadline, batchSize);
        for (OrderInfo order : candidates) {
            try {
                if (lifecycleService.closeExpiredOrder(order, deadline)) {
                    logger.info("超时订单已关闭并回补库存 orderId={} goodsId={}",
                            order.getId(), order.getGoodsId());
                }
            } catch (Exception e) {
                logger.error("超时关单失败 orderId={}", order.getId(), e);
            }
        }
    }
}

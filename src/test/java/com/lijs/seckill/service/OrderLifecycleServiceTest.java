package com.lijs.seckill.service;

import com.lijs.seckill.dao.OrderDao;
import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.redis.GoodsKey;
import com.lijs.seckill.redis.OrderKey;
import com.lijs.seckill.redis.RedisService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleServiceTest {

    @Mock
    private OrderDao orderDao;
    @Mock
    private GoodsService goodsService;
    @Mock
    private RedisService redisService;
    @Mock
    private SeckillService seckillService;

    private OrderLifecycleService orderLifecycleService;

    @BeforeEach
    void setUp() {
        orderLifecycleService = new OrderLifecycleService(
                orderDao, goodsService, redisService, seckillService, new SimpleMeterRegistry());
    }

    @Test
    void closeExpiredOrderRestoresStockAndCacheWhenSuccessful() {
        OrderInfo candidate = new OrderInfo();
        candidate.setId(11L);
        candidate.setUserId(7L);
        candidate.setGoodsId(9L);
        candidate.setGoodsCount(2);

        Date deadline = new Date();
        when(orderDao.closeIfUnpaidAndExpired(11L, deadline)).thenReturn(1);

        boolean closed = orderLifecycleService.closeExpiredOrder(candidate, deadline);

        assertTrue(closed);
        verify(goodsService).increaseStock(9L, 2);
        verify(orderDao).deleteSeckillOrderByOrderId(11L);
        verify(redisService).delete(OrderKey.getSeckillOrderByUidAndGid, "7_9");
        verify(redisService).increaseBy(GoodsKey.getSeckillGoodsStock, "9", 2);
        verify(seckillService).clearGoodsOver(9L);
    }

    @Test
    void closeExpiredOrderSkipsAlreadyProcessedOrder() {
        OrderInfo candidate = new OrderInfo();
        candidate.setId(11L);
        candidate.setUserId(7L);
        candidate.setGoodsId(9L);
        candidate.setGoodsCount(2);

        Date deadline = new Date();
        when(orderDao.closeIfUnpaidAndExpired(11L, deadline)).thenReturn(0);

        boolean closed = orderLifecycleService.closeExpiredOrder(candidate, deadline);

        assertFalse(closed);
        verify(goodsService, never()).increaseStock(anyLong(), anyInt());
        verify(orderDao, never()).deleteSeckillOrderByOrderId(anyLong());
        verify(redisService, never()).delete(any(), any());
        verify(redisService, never()).increaseBy(any(), any(), anyInt());
        verify(seckillService, never()).clearGoodsOver(anyLong());
    }

    @Test
    void cancelUnpaidOrderRestoresStockAndRemovesSeckillMapping() {
        OrderInfo order = new OrderInfo();
        order.setId(12L);
        order.setUserId(7L);
        order.setGoodsId(9L);
        order.setGoodsCount(1);
        when(orderDao.getOrderByOrderId(12L)).thenReturn(order);
        when(orderDao.cancelIfUnpaid(12L, 7L)).thenReturn(1);

        boolean cancelled = orderLifecycleService.cancelUnpaidOrder(12L, 7L);

        assertTrue(cancelled);
        verify(goodsService).increaseStock(9L, 1);
        verify(orderDao).deleteSeckillOrderByOrderId(12L);
        verify(redisService).delete(OrderKey.getSeckillOrderByUidAndGid, "7_9");
        verify(redisService).increaseBy(GoodsKey.getSeckillGoodsStock, "9", 1);
        verify(seckillService).clearGoodsOver(9L);
    }

    @Test
    void shipPaidOrderStoresTrimmedLogisticsDetails() {
        when(orderDao.markShippedIfPaid(21L, "顺丰速运", "SF123456")).thenReturn(1);

        boolean shipped = orderLifecycleService.shipPaidOrder(21L, "  顺丰速运 ", " SF123456 ");

        assertTrue(shipped);
        verify(orderDao).markShippedIfPaid(21L, "顺丰速运", "SF123456");
    }

    @Test
    void shipPaidOrderRejectsMissingOrOversizedLogisticsDetails() {
        assertFalse(orderLifecycleService.shipPaidOrder(21L, " ", "SF123456"));
        assertFalse(orderLifecycleService.shipPaidOrder(21L, "顺丰速运", " "));
        assertFalse(orderLifecycleService.shipPaidOrder(21L, "x".repeat(33), "SF123456"));
        assertFalse(orderLifecycleService.shipPaidOrder(21L, "顺丰速运", "x".repeat(65)));

        verify(orderDao, never()).markShippedIfPaid(anyLong(), any(), any());
    }

    @Test
    void requestRefundTrimsReasonAndDelegatesToDao() {
        when(orderDao.requestRefundIfEligible(31L, 7L, "商品有瑕疵")).thenReturn(1);

        boolean requested = orderLifecycleService.requestRefund(31L, 7L, "  商品有瑕疵 ");

        assertTrue(requested);
        verify(orderDao).requestRefundIfEligible(31L, 7L, "商品有瑕疵");
    }

    @Test
    void requestRefundRejectsBlankOrOversizedReason() {
        assertFalse(orderLifecycleService.requestRefund(31L, 7L, " "));
        assertFalse(orderLifecycleService.requestRefund(31L, 7L, "x".repeat(256)));
        verify(orderDao, never()).requestRefundIfEligible(anyLong(), anyLong(), any());
    }

    @Test
    void approveRefundOnlySucceedsForPendingRequest() {
        when(orderDao.markRefundedIfRequested(31L)).thenReturn(1);

        assertTrue(orderLifecycleService.approveRefund(31L));
        verify(orderDao).markRefundedIfRequested(31L);
    }
}

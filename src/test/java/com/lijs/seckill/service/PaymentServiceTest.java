package com.lijs.seckill.service;

import com.lijs.seckill.dao.OrderDao;
import com.lijs.seckill.dao.PaymentCallbackEventDao;
import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.domain.OrderStatus;
import com.lijs.seckill.domain.PaymentCallbackEvent;
import com.lijs.seckill.vo.MockPaymentVo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderDao orderDao;
    @Mock
    private PaymentCallbackEventDao callbackEventDao;

    private SimpleMeterRegistry meterRegistry;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        paymentService = new PaymentService(orderDao, callbackEventDao, meterRegistry);
    }

    @Test
    void createPaymentReturnsNullForForeignOrder() {
        OrderInfo order = buildOrder(11L, 2L, OrderStatus.UNPAID.getCode(), null);
        when(orderDao.getOrderByOrderId(11L)).thenReturn(order);

        MockPaymentVo payment = paymentService.createPayment(11L, 1L);

        assertNull(payment);
        verify(orderDao, never()).setPaymentNoIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    void createPaymentAssignsPaymentNoForUnpaidOrder() {
        OrderInfo first = buildOrder(11L, 1L, OrderStatus.UNPAID.getCode(), null);
        OrderInfo second = buildOrder(11L, 1L, OrderStatus.UNPAID.getCode(), "MOCK-001");
        second.setGoodsPrice(99.0);
        when(orderDao.getOrderByOrderId(11L)).thenReturn(first, second);

        MockPaymentVo payment = paymentService.createPayment(11L, 1L);

        assertNotNull(payment);
        assertEquals(11L, payment.getOrderId());
        assertEquals("MOCK-001", payment.getPaymentNo());
        assertEquals(99.0, payment.getAmount());
        assertEquals(OrderStatus.UNPAID.getCode(), payment.getOrderStatus());
        verify(orderDao).setPaymentNoIfAbsent(eq(11L), eq(1L), anyString());
    }

    @Test
    void handleSuccessCallbackMarksPaidAndPersistsCallbackEvent() {
        OrderInfo order = buildOrder(11L, 1L, OrderStatus.UNPAID.getCode(), "MOCK-001");
        order.setGoodsPrice(88.0);
        when(callbackEventDao.findByTransaction(PaymentService.PROVIDER, "txn-1")).thenReturn(null);
        when(orderDao.getOrderByPaymentNo("MOCK-001")).thenReturn(order);
        when(callbackEventDao.insertEvent(any(PaymentCallbackEvent.class))).thenAnswer(invocation -> {
            PaymentCallbackEvent event = invocation.getArgument(0);
            event.setId(101L);
            return 1;
        });
        when(orderDao.markPaidIfUnpaid(11L, "txn-1")).thenReturn(1);
        when(callbackEventDao.updateResult(101L, "PAID")).thenReturn(1);

        String result = paymentService.handleSuccessCallback("MOCK-001", "txn-1", 1L);

        assertEquals("PAID", result);
        ArgumentCaptor<PaymentCallbackEvent> captor = ArgumentCaptor.forClass(PaymentCallbackEvent.class);
        verify(callbackEventDao).insertEvent(captor.capture());
        PaymentCallbackEvent saved = captor.getValue();
        assertEquals(11L, saved.getOrderId());
        assertEquals("MOCK-001", saved.getPaymentNo());
        assertEquals(PaymentService.PROVIDER, saved.getProvider());
        assertEquals("txn-1", saved.getTransactionId());
        assertEquals("PROCESSING", saved.getProcessResult());
        verify(callbackEventDao).updateResult(101L, "PAID");
    }

    @Test
    void handleSuccessCallbackReturnsDuplicateForRepeatedTransaction() {
        PaymentCallbackEvent saved = new PaymentCallbackEvent();
        saved.setProcessResult("PAID");
        when(callbackEventDao.findByTransaction(PaymentService.PROVIDER, "txn-dup")).thenReturn(saved);

        String result = paymentService.handleSuccessCallback("MOCK-001", "txn-dup", 1L);

        assertEquals("DUPLICATE", result);
        verify(callbackEventDao, never()).insertEvent(any());
        verify(orderDao, never()).getOrderByPaymentNo(any());
    }

    private OrderInfo buildOrder(long orderId, long userId, int status, String paymentNo) {
        OrderInfo order = new OrderInfo();
        order.setId(orderId);
        order.setUserId(userId);
        order.setOrderStatus(status);
        order.setPaymentNo(paymentNo);
        return order;
    }
}

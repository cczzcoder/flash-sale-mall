package com.lijs.seckill.service;

import com.lijs.seckill.dao.PaymentCallbackEventDao;
import com.lijs.seckill.dao.OrderDao;
import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.domain.OrderStatus;
import com.lijs.seckill.domain.PaymentCallbackEvent;
import com.lijs.seckill.util.UUIDUtil;
import com.lijs.seckill.vo.MockPaymentVo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/** 模拟支付服务。仅用于项目演示，真实支付必须增加平台签名验签。 */
@Service
public class PaymentService {

    public static final String PROVIDER = "MOCK";

    private final OrderDao orderDao;
    private final PaymentCallbackEventDao callbackEventDao;
    private final MeterRegistry meterRegistry;

    public PaymentService(OrderDao orderDao, PaymentCallbackEventDao callbackEventDao,
                          MeterRegistry meterRegistry) {
        this.orderDao = orderDao;
        this.callbackEventDao = callbackEventDao;
        this.meterRegistry = meterRegistry;
    }

    public MockPaymentVo createPayment(long orderId, long userId) {
        OrderInfo order = orderDao.getOrderByOrderId(orderId);
        if (order == null || order.getUserId() == null || order.getUserId() != userId) {
            return null;
        }
        if (!OrderStatus.UNPAID.getCodeEquals(order.getOrderStatus())) {
            return toVo(order);
        }
        if (order.getPaymentNo() == null) {
            String paymentNo = "MOCK" + UUIDUtil.uuid();
            orderDao.setPaymentNoIfAbsent(orderId, userId, paymentNo);
            order = orderDao.getOrderByOrderId(orderId);
        }
        return toVo(order);
    }

    /**
     * @return PAID、DUPLICATE、CLOSED 或 INVALID。
     */
    @Transactional
    public String handleSuccessCallback(String paymentNo, String transactionId, long userId) {
        PaymentCallbackEvent existing = callbackEventDao.findByTransaction(PROVIDER, transactionId);
        if (existing != null) {
            recordCallback("duplicate");
            return "DUPLICATE";
        }
        OrderInfo order = orderDao.getOrderByPaymentNo(paymentNo);
        if (order == null || order.getUserId() == null || order.getUserId() != userId) {
            recordCallback("invalid");
            return "INVALID";
        }
        PaymentCallbackEvent event = new PaymentCallbackEvent();
        event.setOrderId(order.getId());
        event.setPaymentNo(paymentNo);
        event.setProvider(PROVIDER);
        event.setTransactionId(transactionId);
        event.setProcessResult("PROCESSING");
        event.setCreateDate(new Date());
        try {
            callbackEventDao.insertEvent(event);
        } catch (DuplicateKeyException duplicate) {
            recordCallback("duplicate");
            return "DUPLICATE";
        }
        String result;
        if (orderDao.markPaidIfUnpaid(order.getId(), transactionId) == 1) {
            result = "PAID";
        } else {
            OrderInfo current = orderDao.getOrderByOrderId(order.getId());
            result = OrderStatus.PAID.getCodeEquals(current.getOrderStatus()) ? "DUPLICATE" :
                    (OrderStatus.CLOSED.getCodeEquals(current.getOrderStatus()) ? "CLOSED" : "INVALID");
        }
        callbackEventDao.updateResult(event.getId(), result);
        recordCallback(result.toLowerCase());
        return result;
    }

    private void recordCallback(String outcome) {
        Counter.builder("payment.callback.total")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private MockPaymentVo toVo(OrderInfo order) {
        MockPaymentVo vo = new MockPaymentVo();
        vo.setOrderId(order.getId());
        vo.setPaymentNo(order.getPaymentNo());
        vo.setAmount(order.getGoodsPrice());
        vo.setOrderStatus(order.getOrderStatus());
        return vo;
    }
}

package com.lijs.seckill.controller;

import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.PaymentService;
import com.lijs.seckill.vo.MockPaymentCallbackVo;
import com.lijs.seckill.vo.MockPaymentVo;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** 项目演示用模拟支付接口，不接触真实资金。 */
@RestController
@RequestMapping("/payment/mock")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create")
    public Result<MockPaymentVo> create(SeckillUser user, @RequestParam("orderId") long orderId) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        MockPaymentVo payment = paymentService.createPayment(orderId, user.getId());
        return payment == null ? Result.error(ResultCode.ORDER_NOT_EXIST) : Result.success(payment);
    }

    @PostMapping("/callback")
    public Result<String> callback(SeckillUser user, @Valid @RequestBody MockPaymentCallbackVo callback) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        String result = paymentService.handleSuccessCallback(
                callback.getPaymentNo(), callback.getTransactionId(), user.getId());
        if ("PAID".equals(result) || "DUPLICATE".equals(result)) {
            return Result.success(result);
        }
        return "CLOSED".equals(result)
                ? Result.error(ResultCode.ORDER_CLOSED)
                : Result.error(ResultCode.PAYMENT_FAILED);
    }
}

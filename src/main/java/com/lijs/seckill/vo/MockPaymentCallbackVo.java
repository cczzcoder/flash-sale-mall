package com.lijs.seckill.vo;

import javax.validation.constraints.NotBlank;

/** 模拟支付回调参数。真实支付场景应替换为支付平台签名验签。 */
public class MockPaymentCallbackVo {
    @NotBlank
    private String paymentNo;
    @NotBlank
    private String transactionId;

    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
}

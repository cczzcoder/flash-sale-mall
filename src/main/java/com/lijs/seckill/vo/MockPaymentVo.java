package com.lijs.seckill.vo;

/** 创建模拟支付单后的返回数据。 */
public class MockPaymentVo {
    private Long orderId;
    private String paymentNo;
    private Double amount;
    private Integer orderStatus;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public Integer getOrderStatus() { return orderStatus; }
    public void setOrderStatus(Integer orderStatus) { this.orderStatus = orderStatus; }
}

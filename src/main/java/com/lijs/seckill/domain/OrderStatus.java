package com.lijs.seckill.domain;

/**
 * 订单状态。状态迁移必须通过带原状态条件的 SQL 完成，避免支付与关单并发覆盖。
 */
public enum OrderStatus {
    UNPAID(0, "待支付"),
    PAID(1, "待发货"),
    SHIPPED(2, "已发货"),
    RECEIVED(3, "已收货"),
    CLOSED(4, "已关闭"),
    COMPLETED(5, "已完成"),
    REFUNDED(6, "已退款");

    private final int code;
    private final String description;

    OrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean getCodeEquals(Integer value) {
        return value != null && value == code;
    }
}

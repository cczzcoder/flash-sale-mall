package com.lijs.seckill.vo;

import com.lijs.seckill.domain.OrderInfo;

/**
 * 订单详情 VO，用于 {@link com.lijs.seckill.controller.OrderController#info} 接口响应。
 *
 * <p>将订单信息（OrderInfo）和商品信息（GoodsVo）合并到同一个对象，
 * 避免前端发起两次请求，一次返回订单详情页所需的全部数据。
 */
public class OrderDetailVo {

    /** 商品视图对象，包含商品名称、图片、秒杀价格等展示信息 */
    private GoodsVo goodsVo;

    /** 订单信息，包含订单状态、下单时间、数量、实付金额等 */
    private OrderInfo order;

    public GoodsVo  getGoodsVo()             { return goodsVo; }
    public void     setGoodsVo(GoodsVo v)    { this.goodsVo = v; }
    public OrderInfo getOrder()              { return order; }
    public void      setOrder(OrderInfo o)   { this.order = o; }
}

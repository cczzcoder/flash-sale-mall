package com.lijs.seckill.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.domain.SeckillOrder;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;

@Mapper
public interface OrderDao extends BaseMapper<OrderInfo> {

    // ---- SeckillOrder 相关（跨表，保留注解 SQL）----

    @Select("select * from seckill_order where user_id=#{userId} and goods_id=#{goodsId}")
    SeckillOrder getSeckillOrderByUserIdAndGoodsId(@Param("userId") Long userId, @Param("goodsId") Long goodsId);

    @Insert("insert into seckill_order (user_id,goods_id,order_id) values (#{userId},#{goodsId},#{orderId})")
    void insertSeckillOrder(SeckillOrder seckillOrder);

    // ---- OrderInfo 相关 ----

    /**
     * order_info 的 insert 由 BaseMapper.insert(OrderInfo) 接管；
     * 插入后 MP 会通过 @TableId(AUTO) 将数据库生成的主键回填到实体的 id 字段。
     * OrderService 中直接取 orderInfo.getId() 即可，无需再单独查一次。
     */

    @Select("select * from order_info where user_id=#{userId} and goods_id=#{goodsId}")
    OrderInfo selectOrderInfo(@Param("userId") Long userId, @Param("goodsId") Long goodsId);

    @Select("select * from order_info where id=#{orderId}")
    OrderInfo getOrderByOrderId(@Param("orderId") long orderId);

    @Select("select * from order_info where payment_no=#{paymentNo}")
    OrderInfo getOrderByPaymentNo(@Param("paymentNo") String paymentNo);

    @Update("update order_info set payment_no=#{paymentNo} " +
            "where id=#{orderId} and user_id=#{userId} and order_status=0 and payment_no is null")
    int setPaymentNoIfAbsent(@Param("orderId") long orderId,
                             @Param("userId") long userId,
                             @Param("paymentNo") String paymentNo);

    @Update("update order_info set order_status=1, pay_date=now(), payment_transaction_id=#{transactionId} " +
            "where id=#{orderId} and order_status=0")
    int markPaidIfUnpaid(@Param("orderId") long orderId,
                         @Param("transactionId") String transactionId);

    @Select("select * from order_info where order_status=0 and create_date < #{deadline} " +
            "order by create_date asc, id asc limit #{limit}")
    List<OrderInfo> selectExpiredUnpaid(@Param("deadline") Date deadline, @Param("limit") int limit);

    @Update("update order_info set order_status=4, close_date=now() " +
            "where id=#{orderId} and order_status=0 and create_date < #{deadline}")
    int closeIfUnpaidAndExpired(@Param("orderId") long orderId, @Param("deadline") Date deadline);

    @Delete("delete from seckill_order where order_id=#{orderId}")
    int deleteSeckillOrderByOrderId(@Param("orderId") long orderId);

    /** 查询用户最近 N 条订单，用于 AI 个性化推荐 */
    @Select("select * from order_info where user_id=#{userId} order by create_date desc limit #{limit}")
    List<OrderInfo> selectRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Update("update order_info set order_status=4, close_date=now() " +
            "where id=#{orderId} and user_id=#{userId} and order_status=0")
    int cancelIfUnpaid(@Param("orderId") long orderId, @Param("userId") long userId);

    @Update("update order_info set order_status=2, shipping_company=#{shippingCompany}, " +
            "tracking_number=#{trackingNumber}, shipped_date=now() " +
            "where id=#{orderId} and order_status=1")
    int markShippedIfPaid(@Param("orderId") long orderId,
                          @Param("shippingCompany") String shippingCompany,
                          @Param("trackingNumber") String trackingNumber);

    @Update("update order_info set order_status=3 where id=#{orderId} and user_id=#{userId} and order_status=2")
    int markReceivedIfShipped(@Param("orderId") long orderId, @Param("userId") long userId);

    @Update("update order_info set order_status=5 where id=#{orderId} and user_id=#{userId} and order_status=3")
    int markCompletedIfReceived(@Param("orderId") long orderId, @Param("userId") long userId);

    @Update("update order_info set refund_reason=#{reason}, refund_request_date=now() " +
            "where id=#{orderId} and user_id=#{userId} and order_status in (1,2,3,5) and refund_request_date is null")
    int requestRefundIfEligible(@Param("orderId") long orderId, @Param("userId") long userId,
                                @Param("reason") String reason);

    @Update("update order_info set order_status=6, refund_date=now() " +
            "where id=#{orderId} and refund_request_date is not null and order_status in (1,2,3,5)")
    int markRefundedIfRequested(@Param("orderId") long orderId);

    @Select("select * from order_info order by create_date desc, id desc limit #{limit}")
    List<OrderInfo> selectRecent(@Param("limit") int limit);
}

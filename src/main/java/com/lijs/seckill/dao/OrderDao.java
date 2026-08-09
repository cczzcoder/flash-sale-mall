package com.lijs.seckill.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.domain.SeckillOrder;
import org.apache.ibatis.annotations.*;

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

    /** 查询用户最近 N 条订单，用于 AI 个性化推荐 */
    @Select("select * from order_info where user_id=#{userId} order by create_date desc limit #{limit}")
    List<OrderInfo> selectRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);
}

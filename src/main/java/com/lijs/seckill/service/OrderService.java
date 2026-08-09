package com.lijs.seckill.service;

import com.lijs.seckill.vo.GoodsVo;
import com.lijs.seckill.dao.OrderDao;
import com.lijs.seckill.domain.SeckillOrder;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.redis.OrderKey;
import com.lijs.seckill.redis.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class OrderService {

    private final Logger logger = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    private OrderDao orderDao;
    @Autowired
    private RedisService redisService;

    /**
     * 判断是否秒杀到某商品，即去seckill_order里面去查找是否有记录userId和goodsId的一条数据。
     * 根据用户userId和goodsId判断是否有者条订单记录，有则返回此纪录
     */
    public SeckillOrder getSeckillOrderByUserIdAndGoodsIdCache(Long userId, Long goodsId) {
        return redisService.get(OrderKey.getSeckillOrderByUidAndGid, userId + "_" + goodsId, SeckillOrder.class);
    }

    /**
     * 生成订单同时写入到缓存。
     * MP 的 insert() 执行后会将数据库自增主键回填到 orderInfo.id，
     * 无需再额外查询一次 order_info 表。
     */
    @Transactional
    public OrderInfo createCacheOrder(SeckillUser user, GoodsVo goodsVo) {
        // 1.生成 order_info 订单，MP insert 后 id 自动回填
        OrderInfo orderInfo = buildOrder(goodsVo.getId(), goodsVo.getSeckillPrice(), user.getId());
        orderDao.insert(orderInfo);
        long orderId = orderInfo.getId();
        logger.info("orderId:{}", orderId);
        // 2.生成秒杀订单 seckill_order
        SeckillOrder seckillOrder = new SeckillOrder();
        seckillOrder.setGoodsId(goodsVo.getId());
        seckillOrder.setOrderId(orderId);
        seckillOrder.setUserId(user.getId());
        orderDao.insertSeckillOrder(seckillOrder);
        // 3.设置缓存数据（key:用户ID_商品ID value:秒杀订单）
        redisService.set(OrderKey.getSeckillOrderByUidAndGid, user.getId() + "_" + goodsVo.getId(), seckillOrder);
        return orderInfo;
    }

    /**
     * 根据用户userId和goodsId判断是否有者条订单记录，有则返回此纪录
     */
    public SeckillOrder getSeckillOrderByUserIdAndGoodsId(Long userId, Long goodsId) {
        return orderDao.getSeckillOrderByUserIdAndGoodsId(userId, goodsId);
    }


    /**
     * 生成订单
     */
    @Transactional
    public OrderInfo createOrderWithoutCache(SeckillUser user, GoodsVo goodsVo) {
        // 1.生成订单order_info
        OrderInfo orderInfo = buildOrder(goodsVo.getId(), goodsVo.getSeckillPrice(), user.getId());
        orderDao.insert(orderInfo);
        // 2.生成秒杀订单seckill_order
        SeckillOrder seckillOrder = new SeckillOrder();
        seckillOrder.setGoodsId(goodsVo.getId());
        // 3.将订单id传给秒杀订单里面的订单 orderId
        seckillOrder.setOrderId(orderInfo.getId());
        seckillOrder.setUserId(user.getId());
        orderDao.insertSeckillOrder(seckillOrder);
        return orderInfo;
    }

    private OrderInfo buildOrder(Long goodsId, Double seckillPrice, Long userId) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setDeliveryAddrId(0L);
        orderInfo.setCreateDate(new Date());
        orderInfo.setGoodsCount(1);
        orderInfo.setGoodsId(goodsId);
        // 秒杀价格
        orderInfo.setGoodsPrice(seckillPrice);
        orderInfo.setOrderChannel(1);
        // 订单状态  0-新建未支付  1-已支付  2-已发货  3-已收货
        orderInfo.setOrderStatus(0);
        orderInfo.setUserId(userId);
        return orderInfo;
    }

    public OrderInfo getOrderByOrderId(long orderId) {
        return orderDao.getOrderByOrderId(orderId);
    }

}

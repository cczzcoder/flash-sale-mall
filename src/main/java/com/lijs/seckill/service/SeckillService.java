package com.lijs.seckill.service;

import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.domain.SeckillOrder;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.redis.SeckillKey;
import com.lijs.seckill.util.MD5Util;
import com.lijs.seckill.util.UUIDUtil;
import com.lijs.seckill.vo.GoodsVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillService {

    private static final Logger logger = LoggerFactory.getLogger(SeckillService.class);

    @Autowired
    private GoodsService goodsService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private RedisService redisService;

    /**
     * 一个事务原子操作【1.库存减1 2.下订单 3.写入秒杀订单】
     * 成功则返回生成的订单
     * 在执行时，数据库会给满足条件的 goods_id 这一行 加上 行锁，防止多个事务同时扣减 同一条记录。
     * 前提：数据库必须在 事务（Transaction）中 执行，才会有行锁，否则可能出现并发问题。
     */
    @Transactional
    public OrderInfo seckillWithCache(SeckillUser user, GoodsVo goodsVo) {
        return seckillWithCache(user, goodsVo, null);
    }

    @Transactional
    public OrderInfo seckillWithCache(SeckillUser user, GoodsVo goodsVo, Long deliveryAddrId) {
        return doSeckill(user, goodsVo, deliveryAddrId, true);
    }

    @Transactional
    public OrderInfo seckill(SeckillUser user, GoodsVo goodsvo) {
        return seckill(user, goodsvo, null);
    }

    @Transactional
    public OrderInfo seckill(SeckillUser user, GoodsVo goodsvo, Long deliveryAddrId) {
        return doSeckill(user, goodsvo, deliveryAddrId, false);
    }

    /**
     * 共享核心：DB 扣库存成功后下订单（order_info + seckill_order），
     * 库存耗尽则标记售罄并返回 null。withCache 决定是否同步写秒杀订单缓存。
     */
    private OrderInfo doSeckill(SeckillUser user, GoodsVo goodsVo, Long deliveryAddrId, boolean withCache) {
        // stock_count > 0 时才执行，受影响行数 = 0 表示库存已耗尽
        boolean success = goodsService.reduceStock(goodsVo);
        if (!success) {
            // 库存不足：标记商品售罄，不写订单，事务回滚无副作用
            setGoodsOver(goodsVo.getId());
            return null;
        }
        return withCache
                ? orderService.createCacheOrder(user, goodsVo, deliveryAddrId)
                : orderService.createOrderWithoutCache(user, goodsVo, deliveryAddrId);
    }

    /**
     * 获取秒杀结果
     * 成功返回id
     * 失败返回0或-1
     * 0代表排队中
     * -1代表库存不足
     *
     * @param userId  用户ID
     * @param goodsId 商品ID
     * @return 秒杀结果
     */
    public long getSeckillResult(Long userId, long goodsId) {
        SeckillOrder order = orderService.getSeckillOrderByUserIdAndGoodsIdCache(userId, goodsId);
        // 秒杀成功
        if (order != null) {
            logger.info("秒杀成功：userId={}, goodsId={}, orderId:{}", userId, goodsId, order.getId());
            return order.getOrderId();
        } else {
            // 查看商品是否卖完了
            boolean isOver = getGoodsOver(goodsId);
            if (isOver) {
                // 商品卖完了
                return -1;
            } else {
                // 商品没有卖完
                return 0;
            }
        }
    }

    /**
     * 先写入缓存
     */
    private void setGoodsOver(Long goodsId) {
        redisService.set(SeckillKey.isGoodsOver, "" + goodsId, true);
    }

    private boolean getGoodsOver(Long goodsId) {
        return redisService.existsKey(SeckillKey.isGoodsOver, "" + goodsId);
    }

    /** 订单关闭并回补库存后清除售罄标记，使恢复的库存可以重新参与秒杀。 */
    public void clearGoodsOver(Long goodsId) {
        redisService.delete(SeckillKey.isGoodsOver, "" + goodsId);
    }

    /**
     * 去缓存里面检查path是否正确，验证path。
     * 验证通过后立即删除，确保 Path 一次性有效，防止同一 Token 被重复利用。
     */
    public boolean checkPath(SeckillUser user, long goodsId, String path) {
        if (user == null || path == null) {
            return false;
        }
        String redisKey = "" + user.getId() + "_" + goodsId;
        String pathRedis = redisService.get(SeckillKey.getSeckillPath, redisKey, String.class);
        if (path.equals(pathRedis)) {
            // 验证通过，立即删除，一次性消费
            redisService.delete(SeckillKey.getSeckillPath, redisKey);
            return true;
        }
        return false;
    }

    /**
     * 生成一个秒杀path，写入缓存，并且，返回至前台
     */
    public String createSeckillPath(SeckillUser user, Long goodsId) {
        String str = MD5Util.md5(UUIDUtil.uuid() + "123456");
        // 将随机串保存在客户端，并且返回至客户端。
        redisService.set(SeckillKey.getSeckillPath, user.getId() + "_" + goodsId, str);
        return str;
    }

}

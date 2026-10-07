package com.lijs.seckill.service;

import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.redis.SeckillKey;
import com.lijs.seckill.vo.GoodsVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillServiceTest {

    @Mock
    private GoodsService goodsService;
    @Mock
    private OrderService orderService;
    @Mock
    private RedisService redisService;

    private SeckillService seckillService;
    private SeckillUser user;
    private GoodsVo goodsVo;

    @BeforeEach
    void setUp() {
        seckillService = new SeckillService();
        ReflectionTestUtils.setField(seckillService, "goodsService", goodsService);
        ReflectionTestUtils.setField(seckillService, "orderService", orderService);
        ReflectionTestUtils.setField(seckillService, "redisService", redisService);
        user = new SeckillUser();
        user.setId(7L);
        goodsVo = new GoodsVo();
        goodsVo.setId(9L);
    }

    @Test
    void seckillWithCacheDelegatesToCacheOrderWhenStockReduced() {
        when(goodsService.reduceStock(goodsVo)).thenReturn(true);
        OrderInfo order = new OrderInfo();
        when(orderService.createCacheOrder(user, goodsVo, 5L)).thenReturn(order);

        assertSame(order, seckillService.seckillWithCache(user, goodsVo, 5L));

        verify(orderService, never()).createOrderWithoutCache(any(SeckillUser.class), any(GoodsVo.class), any());
    }

    @Test
    void seckillWithCacheMarksGoodsOverWhenStockExhausted() {
        when(goodsService.reduceStock(goodsVo)).thenReturn(false);

        assertNull(seckillService.seckillWithCache(user, goodsVo, 5L));

        verify(redisService).set(SeckillKey.isGoodsOver, "9", true);
        verify(orderService, never()).createCacheOrder(any(SeckillUser.class), any(GoodsVo.class), any());
    }

    @Test
    void seckillWithoutCacheDelegatesToUncachedOrderWhenStockReduced() {
        when(goodsService.reduceStock(goodsVo)).thenReturn(true);
        OrderInfo order = new OrderInfo();
        when(orderService.createOrderWithoutCache(user, goodsVo, null)).thenReturn(order);

        assertSame(order, seckillService.seckill(user, goodsVo));

        verify(orderService, never()).createCacheOrder(any(SeckillUser.class), any(GoodsVo.class), any());
    }

    @Test
    void seckillWithoutCacheMarksGoodsOverWhenStockExhausted() {
        when(goodsService.reduceStock(goodsVo)).thenReturn(false);

        assertNull(seckillService.seckill(user, goodsVo, 5L));

        verify(redisService).set(SeckillKey.isGoodsOver, "9", true);
        verify(orderService, never()).createOrderWithoutCache(any(SeckillUser.class), any(GoodsVo.class), any());
    }
}

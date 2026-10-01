package com.lijs.seckill.controller;

import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.redis.SeckillKey;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.DeliveryAddressService;
import com.lijs.seckill.service.GoodsService;
import com.lijs.seckill.service.OrderService;
import com.lijs.seckill.service.SeckillService;
import com.lijs.seckill.vo.GoodsVo;
import com.lijs.seckill.vo.SeckillWindowVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class SeckillControllerTest {

    @Mock
    private GoodsService goodsService;
    @Mock
    private RedisService redisService;
    @Mock
    private SeckillService seckillService;
    @Mock
    private OrderService orderService;
    @Mock
    private DeliveryAddressService deliveryAddressService;

    private SeckillController controller;
    private SeckillUser user;

    @BeforeEach
    void setUp() {
        controller = new SeckillController();
        ReflectionTestUtils.setField(controller, "goodsService", goodsService);
        ReflectionTestUtils.setField(controller, "redisService", redisService);
        ReflectionTestUtils.setField(controller, "seckillService", seckillService);
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        ReflectionTestUtils.setField(controller, "deliveryAddressService", deliveryAddressService);
        user = new SeckillUser();
        user.setId(10001L);
    }

    @Test
    void getPathRejectsActivityBeforeStart() {
        GoodsVo goods = goodsWithWindow(new Date(System.currentTimeMillis() + 60_000),
                new Date(System.currentTimeMillis() + 120_000));
        when(goodsService.getGoodsVoByGoodsId(1L)).thenReturn(goods);

        Result<String> result = controller.getSeckillPath(mock(HttpServletRequest.class),
                new ExtendedModelMap(), user, 1L, 1);

        assertEquals(ResultCode.SECKILL_NOT_STARTED.getCode(), result.getCode());
    }

    @Test
    void getPathRejectsActivityAfterEnd() {
        GoodsVo goods = goodsWithWindow(new Date(System.currentTimeMillis() - 120_000),
                new Date(System.currentTimeMillis() - 60_000));
        when(goodsService.getGoodsVoByGoodsId(1L)).thenReturn(goods);

        Result<String> result = controller.getSeckillPath(mock(HttpServletRequest.class),
                new ExtendedModelMap(), user, 1L, 1);

        assertEquals(ResultCode.SECKILL_ENDED.getCode(), result.getCode());
    }

    @Test
    void cachedSeckillRejectsActivityBeforeStartBeforeConsumingAddressOrStock() {
        GoodsVo goods = goodsWithWindow(new Date(System.currentTimeMillis() + 60_000),
                new Date(System.currentTimeMillis() + 120_000));
        when(goodsService.getGoodsVoByGoodsId(1L)).thenReturn(goods);

        Result<Integer> result = controller.seckillWithCacheAndMQ(new ExtendedModelMap(), user,
                1L, 10L, "path");

        assertEquals(ResultCode.SECKILL_NOT_STARTED.getCode(), result.getCode());
    }

    @Test
    void startupWarmupDoesNotOverwriteExistingRedisStock() {
        GoodsVo goods = goodsWithWindow(new Date(System.currentTimeMillis() - 60_000),
                new Date(System.currentTimeMillis() + 60_000));
        goods.setStockCount(20);
        when(goodsService.getGoodsVoList()).thenReturn(java.util.Collections.singletonList(goods));

        controller.afterPropertiesSet();

        verify(redisService).setIfAbsent(eq(com.lijs.seckill.redis.GoodsKey.getSeckillGoodsStock), eq("1"), eq(20));
        verify(redisService).set(eq(SeckillKey.getSeckillWindow), eq("1"), any(SeckillWindowVo.class));
    }

    @Test
    void getPathUsesCachedActivityWindowWithoutQueryingGoods() {
        when(redisService.get(eq(SeckillKey.getSeckillWindow), eq("1"), eq(SeckillWindowVo.class)))
                .thenReturn(new SeckillWindowVo(new Date(System.currentTimeMillis() - 60_000),
                        new Date(System.currentTimeMillis() + 60_000)));
        when(redisService.get(eq(com.lijs.seckill.redis.AccessKey.access), anyString(), eq(Integer.class)))
                .thenReturn(null);
        when(seckillService.checkVCode(user, 1L, 123)).thenReturn(true);
        when(seckillService.createSeckillPath(user, 1L)).thenReturn("path");

        Result<String> result = controller.getSeckillPath(mock(HttpServletRequest.class),
                new ExtendedModelMap(), user, 1L, 123);

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        verify(goodsService, org.mockito.Mockito.never()).getGoodsVoByGoodsId(1L);
    }

    @Test
    void getPathCachesWindowAfterDatabaseFallback() {
        GoodsVo goods = goodsWithWindow(new Date(System.currentTimeMillis() - 60_000),
                new Date(System.currentTimeMillis() + 60_000));
        when(redisService.get(eq(SeckillKey.getSeckillWindow), eq("1"), eq(SeckillWindowVo.class)))
                .thenReturn(null);
        when(goodsService.getGoodsVoByGoodsId(1L)).thenReturn(goods);
        when(redisService.get(eq(com.lijs.seckill.redis.AccessKey.access), anyString(), eq(Integer.class)))
                .thenReturn(null);
        when(seckillService.checkVCode(user, 1L, 123)).thenReturn(true);
        when(seckillService.createSeckillPath(user, 1L)).thenReturn("path");

        Result<String> result = controller.getSeckillPath(mock(HttpServletRequest.class),
                new ExtendedModelMap(), user, 1L, 123);

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        verify(redisService).setIfAbsent(eq(SeckillKey.getSeckillWindow), eq("1"), any(SeckillWindowVo.class));
    }

    private GoodsVo goodsWithWindow(Date start, Date end) {
        GoodsVo goods = new GoodsVo();
        goods.setId(1L);
        goods.setStartDate(start);
        goods.setEndDate(end);
        return goods;
    }
}

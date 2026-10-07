package com.lijs.seckill.controller;

import com.lijs.seckill.redis.GoodsKey;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.GoodsService;
import com.lijs.seckill.vo.GoodsVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.Model;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.WebContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoodsControllerTest {

    @Mock private GoodsService goodsService;
    @Mock private RedisService redisService;
    @Mock private ITemplateEngine templateEngine;
    @Mock private Model model;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private GoodsController controller;

    @BeforeEach
    void setUp() {
        controller = new GoodsController();
        ReflectionTestUtils.setField(controller, "goodsService", goodsService);
        ReflectionTestUtils.setField(controller, "redisService", redisService);
        ReflectionTestUtils.setField(controller, "templateEngine", templateEngine);
    }

    // ---------- detailStaticPage：基础校验 + 缓存穿透防护 ----------

    @Test
    void staticDetailReturnsGoodsNotFoundForMissingGoods() {
        when(goodsService.getGoodsVoByGoodsId(9L)).thenReturn(null);

        assertEquals(ResultCode.GOODS_NOT_EXIST.getCode(),
                controller.detailStaticPage(model, null, request, response, 9L).getCode());
    }

    @Test
    void staticDetailRejectsNonPositiveGoodsIdBeforeQueryingDatabase() {
        assertEquals(ResultCode.GOODS_NOT_EXIST.getCode(),
                controller.detailStaticPage(model, null, request, response, 0L).getCode());
        verify(goodsService, never()).getGoodsVoByGoodsId(0L);
    }

    @Test
    void staticDetailReturnsTimeErrorForIncompleteActivityWindow() {
        GoodsVo goods = new GoodsVo();
        when(goodsService.getGoodsVoByGoodsId(9L)).thenReturn(goods);

        assertEquals(ResultCode.GOODS_TIME_INVALID.getCode(),
                controller.detailStaticPage(model, null, request, response, 9L).getCode());
    }

    @Test
    void staticDetailShortCircuitsDatabaseWhenNullCacheHits() {
        when(redisService.existsKey(GoodsKey.getGoodsNull, "9")).thenReturn(true);

        assertEquals(ResultCode.GOODS_NOT_EXIST.getCode(),
                controller.detailStaticPage(model, null, request, response, 9L).getCode());
        verify(goodsService, never()).getGoodsVoByGoodsId(9L);
    }

    @Test
    void staticDetailWritesNullCacheWhenGoodsMissing() {
        when(goodsService.getGoodsVoByGoodsId(9L)).thenReturn(null);

        assertEquals(ResultCode.GOODS_NOT_EXIST.getCode(),
                controller.detailStaticPage(model, null, request, response, 9L).getCode());
        verify(redisService).set(GoodsKey.getGoodsNull, "9", "1");
    }

    // ---------- goodsListWithCache：缓存击穿防护（SETNX 单飞锁） ----------

    @Test
    void listServesCacheDirectlyOnHit() {
        when(redisService.get(GoodsKey.getGoodsList, "", String.class)).thenReturn("<html>cached</html>");

        assertEquals("<html>cached</html>",
                controller.goodsListWithCache(model, null, request, response));
        verify(goodsService, never()).getGoodsVoList();
    }

    @Test
    void listRebuildNonHolderWaitsThenServesFreshCache() {
        when(redisService.get(GoodsKey.getGoodsList, "", String.class))
                .thenReturn(null)
                .thenReturn("<html>cached</html>");
        when(redisService.setIfAbsent(GoodsKey.getGoodsListRebuildLock, "", "1")).thenReturn(false);

        assertEquals("<html>cached</html>",
                controller.goodsListWithCache(model, null, request, response));
        verify(goodsService, never()).getGoodsVoList();
        verify(redisService, never()).delete(GoodsKey.getGoodsListRebuildLock, "");
    }

    @Test
    void listRebuildHolderRendersThenReleasesLock() {
        when(redisService.get(GoodsKey.getGoodsList, "", String.class)).thenReturn(null);
        when(redisService.setIfAbsent(GoodsKey.getGoodsListRebuildLock, "", "1")).thenReturn(true);
        when(goodsService.getGoodsVoList()).thenReturn(Collections.singletonList(new GoodsVo()));
        when(request.getServletContext()).thenReturn(new MockServletContext());
        when(request.getLocale()).thenReturn(Locale.CHINA);
        when(model.asMap()).thenReturn(new HashMap<>());
        when(templateEngine.process(eq("goods_list"), any(WebContext.class)))
                .thenReturn("<html>rendered</html>");

        assertEquals("<html>rendered</html>",
                controller.goodsListWithCache(model, null, request, response));
        verify(redisService).set(GoodsKey.getGoodsList, "", "<html>rendered</html>");
        verify(redisService).delete(GoodsKey.getGoodsListRebuildLock, "");
    }

    @Test
    void listRebuildReleasesLockWhenRebuildFails() {
        when(redisService.get(GoodsKey.getGoodsList, "", String.class)).thenReturn(null);
        when(redisService.setIfAbsent(GoodsKey.getGoodsListRebuildLock, "", "1")).thenReturn(true);
        when(goodsService.getGoodsVoList()).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class,
                () -> controller.goodsListWithCache(model, null, request, response));
        verify(redisService).delete(GoodsKey.getGoodsListRebuildLock, "");
    }
}

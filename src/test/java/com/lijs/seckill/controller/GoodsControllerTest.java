package com.lijs.seckill.controller;

import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.GoodsService;
import com.lijs.seckill.vo.GoodsVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.Model;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoodsControllerTest {

    @Mock private GoodsService goodsService;
    @Mock private Model model;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private GoodsController controller;

    @BeforeEach
    void setUp() {
        controller = new GoodsController();
        ReflectionTestUtils.setField(controller, "goodsService", goodsService);
    }

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

}

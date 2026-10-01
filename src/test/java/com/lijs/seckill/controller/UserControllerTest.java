package com.lijs.seckill.controller;

import com.lijs.seckill.service.GoodsService;
import com.lijs.seckill.service.SeckillUserService;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.vo.GoodsVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.Model;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private GoodsService goodsService;
    @Mock private SeckillUserService seckillUserService;
    @Mock private Model model;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController();
        ReflectionTestUtils.setField(controller, "goodsService", goodsService);
        ReflectionTestUtils.setField(controller, "seckillUserService", seckillUserService);
    }

    @Test
    void invalidGoodsIdReturnsFailurePageWithoutQueryingDatabase() {
        assertEquals("seckill_fail", controller.toDetail(model, null, 0L));
        verify(goodsService, never()).getGoodsVoByGoodsId(0L);
        verify(model).addAttribute("errorMessage", ResultCode.GOODS_NOT_EXIST.getMsg());
    }

    @Test
    void missingGoodsReturnsFailurePage() {
        when(goodsService.getGoodsVoByGoodsId(9L)).thenReturn(null);

        assertEquals("seckill_fail", controller.toDetail(model, null, 9L));
        verify(model).addAttribute("errorMessage", ResultCode.GOODS_NOT_EXIST.getMsg());
    }

    @Test
    void invalidActivityWindowReturnsFailurePage() {
        GoodsVo goods = new GoodsVo();
        goods.setStartDate(new Date(2000L));
        goods.setEndDate(new Date(1000L));
        when(goodsService.getGoodsVoByGoodsId(9L)).thenReturn(goods);

        assertEquals("seckill_fail", controller.toDetail(model, null, 9L));
        verify(model).addAttribute("errorMessage", ResultCode.GOODS_TIME_INVALID.getMsg());
    }
}

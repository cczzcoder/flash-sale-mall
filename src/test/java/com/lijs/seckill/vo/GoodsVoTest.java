package com.lijs.seckill.vo;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoodsVoTest {

    private static final long SECOND = 1000L;

    /** 相对当前时间构建时间窗的商品。 */
    private static GoodsVo withWindow(long startOffsetMillis, long endOffsetMillis) {
        GoodsVo vo = new GoodsVo();
        long now = System.currentTimeMillis();
        vo.setStartDate(new Date(now + startOffsetMillis));
        vo.setEndDate(new Date(now + endOffsetMillis));
        return vo;
    }

    @Test
    void invalidWindowWhenDatesMissingEqualOrReversed() {
        GoodsVo missingStart = new GoodsVo();
        missingStart.setEndDate(new Date());
        assertTrue(missingStart.isInvalidWindow());

        GoodsVo missingEnd = new GoodsVo();
        missingEnd.setStartDate(new Date());
        assertTrue(missingEnd.isInvalidWindow());

        assertTrue(withWindow(0, 0).isInvalidWindow());                // start == end
        assertTrue(withWindow(60 * SECOND, 0).isInvalidWindow());      // start > end
    }

    @Test
    void validWindowWhenStartBeforeEnd() {
        assertFalse(withWindow(-SECOND, SECOND).isInvalidWindow());
    }

    @Test
    void activityStatusReflectsWindow() {
        assertEquals(0, withWindow(60 * SECOND, 120 * SECOND).getActivityStatus());   // 未开始
        assertEquals(1, withWindow(-60 * SECOND, 60 * SECOND).getActivityStatus());   // 进行中
        assertEquals(2, withWindow(-120 * SECOND, -60 * SECOND).getActivityStatus()); // 已结束
        assertEquals(2, new GoodsVo().getActivityStatus()); // 非法窗口按已结束处理
    }

    @Test
    void remainingSecondsReflectsWindow() {
        assertTrue(withWindow(120 * SECOND, 240 * SECOND).getRemainingSeconds() > 0);
        assertEquals(0, withWindow(-60 * SECOND, 60 * SECOND).getRemainingSeconds());
        assertEquals(-1, withWindow(-120 * SECOND, -60 * SECOND).getRemainingSeconds());
        assertEquals(-1, new GoodsVo().getRemainingSeconds());
    }

    @Test
    void soldOutWhenStockMissingOrNonPositive() {
        GoodsVo vo = new GoodsVo();
        assertTrue(vo.isSoldOut());

        vo.setStockCount(0);
        assertTrue(vo.isSoldOut());

        vo.setStockCount(5);
        assertFalse(vo.isSoldOut());
    }

    @Test
    void soldPercentReflectsSoldRatio() {
        GoodsVo vo = new GoodsVo();
        assertEquals(0, vo.getSoldPercent());   // 无总库存信息

        vo.setStockTotal(0);
        assertEquals(0, vo.getSoldPercent());   // 非法分母

        vo.setStockTotal(10);
        vo.setStockCount(10);
        assertEquals(0, vo.getSoldPercent());   // 未售出

        vo.setStockCount(4);
        assertEquals(60, vo.getSoldPercent());

        vo.setStockCount(null);
        assertEquals(100, vo.getSoldPercent()); // 剩余未知按售罄处理

        vo.setStockCount(15);
        assertEquals(0, vo.getSoldPercent());   // 剩余超过总量按未售出兜底
    }
}

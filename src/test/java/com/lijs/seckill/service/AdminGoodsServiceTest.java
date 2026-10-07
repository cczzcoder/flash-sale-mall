package com.lijs.seckill.service;

import com.lijs.seckill.dao.AdminGoodsDao;
import com.lijs.seckill.domain.Goods;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.vo.AdminGoodsVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminGoodsServiceTest {
    @Mock private AdminGoodsDao goodsDao;
    @Mock private RedisService redisService;
    @Mock private SeckillService seckillService;

    private AdminGoodsService service;

    @BeforeEach
    void setUp() {
        service = new AdminGoodsService(goodsDao, redisService, seckillService);
    }

    @Test
    void rejectsInvalidTimeAndPriceBeforeWriting() {
        AdminGoodsVo vo = validGoods();
        vo.setEndDate(vo.getStartDate());

        assertEquals(ResultCode.GOODS_TIME_INVALID.getCode(), service.save(vo).getCode());
        verify(goodsDao, never()).insertGoods(any());

        vo = validGoods();
        vo.setSeckillPrice(20D);
        vo.setGoodsPrice(10D);
        assertEquals(ResultCode.GOODS_PRICE_INVALID.getCode(), service.save(vo).getCode());
        verify(goodsDao, never()).insertSeckill(any());
    }

    @Test
    void refusesDeletingGoodsThatHaveOrders() {
        when(goodsDao.get(4L)).thenReturn(new com.lijs.seckill.vo.GoodsVo());
        when(goodsDao.countOrders(4L)).thenReturn(1);

        assertEquals(ResultCode.GOODS_IN_USE.getCode(), service.delete(4L).getCode());
        verify(goodsDao, never()).deleteGoods(4L);
    }

    @Test
    void editingExistingGoodsPreservesInventoryAndDoesNotRewriteRedisStock() {
        AdminGoodsVo vo = validGoods();
        vo.setGoodsId(4L);
        com.lijs.seckill.vo.GoodsVo current = new com.lijs.seckill.vo.GoodsVo();
        current.setId(4L);
        current.setStockCount(10);
        current.setGoodsStock(100);
        when(goodsDao.get(4L)).thenReturn(current);
        when(goodsDao.updateGoods(any())).thenReturn(1);
        when(goodsDao.updateSeckill(any())).thenReturn(1);

        assertEquals(ResultCode.SUCCESS.getCode(), service.save(vo).getCode());

        verify(goodsDao).updateGoods(any());
        verify(goodsDao).updateSeckill(any());
        verify(redisService, never()).set(any(), anyString(), any());
    }

    @Test
    void rejectsChangingExistingInventoryThroughEditForm() {
        AdminGoodsVo vo = validGoods();
        vo.setGoodsId(4L);
        com.lijs.seckill.vo.GoodsVo current = new com.lijs.seckill.vo.GoodsVo();
        current.setId(4L);
        current.setStockCount(10);
        when(goodsDao.get(4L)).thenReturn(current);
        vo.setStockCount(11);

        assertEquals(ResultCode.GOODS_STOCK_EDIT_FORBIDDEN.getCode(), service.save(vo).getCode());
        verify(goodsDao, never()).updateGoods(any());
        verify(goodsDao, never()).updateSeckill(any());
    }

    @Test
    void restockUpdatesDbAndRedisAfterSuccessfulDatabaseChange() {
        when(goodsDao.get(4L)).thenReturn(new com.lijs.seckill.vo.GoodsVo());
        when(goodsDao.increaseSeckillStock(4L, 5)).thenReturn(1);
        when(goodsDao.increaseGoodsStock(4L, 5)).thenReturn(1);

        assertEquals(ResultCode.SUCCESS.getCode(), service.restock(4L, 5).getCode());

        verify(goodsDao).increaseSeckillStock(4L, 5);
        verify(goodsDao).increaseGoodsStock(4L, 5);
        verify(redisService).increaseBy(any(), anyString(), org.mockito.ArgumentMatchers.eq(5));
        verify(seckillService).clearGoodsOver(4L);
    }

    @Test
    void rejectsNonPositiveRestockWithoutWriting() {
        assertEquals(ResultCode.GOODS_STOCK_INVALID.getCode(), service.restock(4L, 0).getCode());
        verify(goodsDao, never()).get(anyLong());
        verify(redisService, never()).increaseBy(any(), anyString(), anyInt());
    }

    @Test
    void merchantCannotEditGoodsOwnedByAnotherShop() {
        AdminGoodsVo vo = validGoods();
        vo.setGoodsId(4L);
        com.lijs.seckill.vo.GoodsVo current = new com.lijs.seckill.vo.GoodsVo();
        current.setId(4L);
        current.setShopId(8L);
        current.setStockCount(10);
        when(goodsDao.get(4L)).thenReturn(current);

        assertEquals(ResultCode.GOODS_FORBIDDEN.getCode(), service.save(vo, 9L).getCode());
        verify(goodsDao, never()).updateGoods(any());
        verify(goodsDao, never()).updateSeckill(any());
    }

    @Test
    void merchantCanEditOwnGoods() {
        AdminGoodsVo vo = validGoods();
        vo.setGoodsId(4L);
        com.lijs.seckill.vo.GoodsVo current = new com.lijs.seckill.vo.GoodsVo();
        current.setId(4L);
        current.setShopId(8L);
        current.setStockCount(10);
        when(goodsDao.get(4L)).thenReturn(current);
        when(goodsDao.updateGoods(any())).thenReturn(1);
        when(goodsDao.updateSeckill(any())).thenReturn(1);

        assertEquals(ResultCode.SUCCESS.getCode(), service.save(vo, 8L).getCode());
        verify(goodsDao).updateGoods(any());
    }

    @Test
    void merchantCreatedGoodsIsBoundToHisShop() {
        AdminGoodsVo vo = validGoods();
        when(goodsDao.insertGoods(any())).thenReturn(1);
        when(goodsDao.insertSeckill(any())).thenReturn(1);

        assertEquals(ResultCode.SUCCESS.getCode(), service.save(vo, 8L).getCode());

        ArgumentCaptor<Goods> captor = ArgumentCaptor.forClass(Goods.class);
        verify(goodsDao).insertGoods(captor.capture());
        assertEquals(Long.valueOf(8L), captor.getValue().getShopId());
        verify(redisService).set(any(), anyString(), any());
    }

    @Test
    void categoryIsTrimmedAndBlankBecomesNull() {
        AdminGoodsVo vo = validGoods();
        vo.setCategory("  手机数码  ");
        when(goodsDao.insertGoods(any())).thenReturn(1);
        when(goodsDao.insertSeckill(any())).thenReturn(1);
        assertEquals(ResultCode.SUCCESS.getCode(), service.save(vo, 8L).getCode());

        ArgumentCaptor<Goods> captor = ArgumentCaptor.forClass(Goods.class);
        verify(goodsDao).insertGoods(captor.capture());
        assertEquals("手机数码", captor.getValue().getCategory());

        AdminGoodsVo blank = validGoods();
        blank.setCategory("   ");
        assertEquals(ResultCode.SUCCESS.getCode(), service.save(blank, 8L).getCode());

        ArgumentCaptor<Goods> blankCaptor = ArgumentCaptor.forClass(Goods.class);
        verify(goodsDao, org.mockito.Mockito.times(2)).insertGoods(blankCaptor.capture());
        assertEquals(null, blankCaptor.getValue().getCategory());
    }

    @Test
    void merchantCannotRestockOrDeleteForeignGoods() {
        com.lijs.seckill.vo.GoodsVo current = new com.lijs.seckill.vo.GoodsVo();
        current.setId(4L);
        current.setShopId(8L);
        when(goodsDao.get(4L)).thenReturn(current);

        assertEquals(ResultCode.GOODS_FORBIDDEN.getCode(), service.restock(4L, 5, 9L).getCode());
        assertEquals(ResultCode.GOODS_FORBIDDEN.getCode(), service.delete(4L, 9L).getCode());
        verify(goodsDao, never()).increaseSeckillStock(anyLong(), anyInt());
        verify(goodsDao, never()).deleteGoods(anyLong());
    }

    @Test
    void platformAdminScopeNullCanEditAnyGoods() {
        AdminGoodsVo vo = validGoods();
        vo.setGoodsId(4L);
        com.lijs.seckill.vo.GoodsVo current = new com.lijs.seckill.vo.GoodsVo();
        current.setId(4L);
        current.setShopId(8L);
        current.setStockCount(10);
        when(goodsDao.get(4L)).thenReturn(current);
        when(goodsDao.updateGoods(any())).thenReturn(1);
        when(goodsDao.updateSeckill(any())).thenReturn(1);

        assertEquals(ResultCode.SUCCESS.getCode(), service.save(vo).getCode());
        verify(goodsDao).updateGoods(any());
    }

    private AdminGoodsVo validGoods() {
        AdminGoodsVo vo = new AdminGoodsVo();
        vo.setGoodsName("phone");
        vo.setGoodsTitle("phone title");
        vo.setGoodsPrice(100D);
        vo.setSeckillPrice(80D);
        vo.setStockCount(10);
        vo.setStartDate(new Date(1000));
        vo.setEndDate(new Date(2000));
        return vo;
    }
}

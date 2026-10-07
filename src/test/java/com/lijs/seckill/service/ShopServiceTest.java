package com.lijs.seckill.service;

import com.lijs.seckill.dao.SeckillUserDao;
import com.lijs.seckill.dao.ShopDao;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.domain.Shop;
import com.lijs.seckill.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {
    @Mock private ShopDao shopDao;
    @Mock private SeckillUserDao seckillUserDao;

    private ShopService service;

    @BeforeEach
    void setUp() {
        service = new ShopService(shopDao, seckillUserDao);
    }

    @Test
    void applyRejectsBlankNameWithoutWriting() {
        assertEquals(ResultCode.SHOP_NAME_INVALID.getCode(), service.apply(10001L, "   ", null, null).getCode());
        assertEquals(ResultCode.SHOP_NAME_INVALID.getCode(), service.apply(10001L, null, null, null).getCode());
        verify(shopDao, never()).insert(any());
    }

    @Test
    void applyRejectsDuplicateApplication() {
        when(shopDao.selectByOwnerUserId(10001L)).thenReturn(new Shop());

        assertEquals(ResultCode.SHOP_ALREADY_APPLIED.getCode(),
                service.apply(10001L, "好店", null, null).getCode());
        verify(shopDao, never()).insert(any());
    }

    @Test
    void applyHandlesConcurrentDuplicateAsAlreadyApplied() {
        when(shopDao.selectByOwnerUserId(10001L)).thenReturn(null);
        when(shopDao.insert(any())).thenThrow(new DuplicateKeyException("uk_owner_user"));

        assertEquals(ResultCode.SHOP_ALREADY_APPLIED.getCode(),
                service.apply(10001L, "好店", null, null).getCode());
    }

    @Test
    void applyInsertsPendingShopAndTrimsFields() {
        when(shopDao.selectByOwnerUserId(10001L)).thenReturn(null);
        when(shopDao.insert(any())).thenReturn(1);

        assertEquals(ResultCode.SUCCESS.getCode(),
                service.apply(10001L, "  好店  ", "  ", "  描述  ").getCode());

        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopDao).insert(captor.capture());
        Shop saved = captor.getValue();
        assertEquals(10001L, saved.getOwnerUserId());
        assertEquals("好店", saved.getName());
        assertNull(saved.getLogo());
        assertEquals("描述", saved.getDescription());
        assertEquals(Shop.STATUS_PENDING, saved.getStatus());
    }

    @Test
    void reviewRejectsInvalidStatusWithoutTouchingShop() {
        assertEquals(ResultCode.SHOP_STATUS_INVALID.getCode(), service.review(7L, 0).getCode());
        assertEquals(ResultCode.SHOP_STATUS_INVALID.getCode(), service.review(7L, 9).getCode());
        verify(shopDao, never()).updateStatus(anyLong(), anyInt());
        verify(seckillUserDao, never()).updateRole(anyLong(), anyInt());
    }

    @Test
    void reviewMissingShopReturnsNotExist() {
        when(shopDao.selectById(7L)).thenReturn(null);

        assertEquals(ResultCode.SHOP_NOT_EXIST.getCode(), service.review(7L, Shop.STATUS_ACTIVE).getCode());
        verify(shopDao, never()).updateStatus(anyLong(), anyInt());
    }

    @Test
    void reviewApproveActivatesShopAndGrantsMerchantRole() {
        Shop shop = new Shop();
        shop.setId(7L);
        shop.setOwnerUserId(10001L);
        when(shopDao.selectById(7L)).thenReturn(shop);
        when(shopDao.updateStatus(7L, Shop.STATUS_ACTIVE)).thenReturn(1);

        assertEquals(ResultCode.SUCCESS.getCode(), service.review(7L, Shop.STATUS_ACTIVE).getCode());

        verify(shopDao).updateStatus(7L, Shop.STATUS_ACTIVE);
        verify(seckillUserDao).updateRole(10001L, SeckillUser.ROLE_MERCHANT);
    }

    @Test
    void reviewDisableDoesNotGrantRole() {
        Shop shop = new Shop();
        shop.setId(7L);
        shop.setOwnerUserId(10001L);
        when(shopDao.selectById(7L)).thenReturn(shop);
        when(shopDao.updateStatus(7L, Shop.STATUS_DISABLED)).thenReturn(1);

        assertEquals(ResultCode.SUCCESS.getCode(), service.review(7L, Shop.STATUS_DISABLED).getCode());

        verify(shopDao).updateStatus(7L, Shop.STATUS_DISABLED);
        verify(seckillUserDao, never()).updateRole(anyLong(), anyInt());
    }
}

package com.lijs.seckill.service;

import com.lijs.seckill.dao.DeliveryAddressDao;
import com.lijs.seckill.domain.DeliveryAddress;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.vo.DeliveryAddressVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryAddressServiceTest {

    @Mock
    private DeliveryAddressDao addressDao;

    private DeliveryAddressService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryAddressService(addressDao);
    }

    @Test
    void savesNewDefaultAddressAndKeepsOnlyOneDefault() {
        DeliveryAddressVo vo = address("上海市", true);

        assertEquals(ResultCode.SUCCESS.getCode(), service.save(7L, vo).getCode());

        verify(addressDao).insert(any(DeliveryAddress.class));
        verify(addressDao).clearDefault(7L);
        verify(addressDao).updateById(any(DeliveryAddress.class));
    }

    @Test
    void rejectsUpdateForAnotherUsersAddress() {
        DeliveryAddressVo vo = address("北京市", false);
        vo.setId(99L);
        when(addressDao.selectOwned(99L, 7L)).thenReturn(null);

        assertEquals(ResultCode.ADDRESS_NOT_EXIST.getCode(), service.save(7L, vo).getCode());

        verify(addressDao, never()).updateById(any(DeliveryAddress.class));
        verify(addressDao, never()).insert(any(DeliveryAddress.class));
    }

    @Test
    void deleteOnlyDeletesOwnedAddress() {
        when(addressDao.deleteOwned(99L, 7L)).thenReturn(1);

        assertEquals(ResultCode.SUCCESS.getCode(), service.delete(99L, 7L).getCode());
        verify(addressDao).deleteOwned(99L, 7L);
    }

    private DeliveryAddressVo address(String city, boolean isDefault) {
        DeliveryAddressVo vo = new DeliveryAddressVo();
        vo.setReceiverName("测试用户");
        vo.setReceiverMobile("13800138000");
        vo.setProvince("上海");
        vo.setCity(city);
        vo.setDistrict("浦东新区");
        vo.setDetail("世纪大道1号");
        vo.setDefaultAddress(isDefault);
        return vo;
    }
}

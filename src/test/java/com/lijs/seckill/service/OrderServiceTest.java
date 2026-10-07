package com.lijs.seckill.service;

import com.lijs.seckill.dao.DeliveryAddressDao;
import com.lijs.seckill.dao.OrderDao;
import com.lijs.seckill.domain.DeliveryAddress;
import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.domain.SeckillOrder;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.OrderKey;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.vo.GoodsVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderDao orderDao;
    @Mock
    private RedisService redisService;
    @Mock
    private DeliveryAddressDao deliveryAddressDao;

    private OrderService orderService;
    private SeckillUser user;
    private GoodsVo goodsVo;

    @BeforeEach
    void setUp() {
        orderService = new OrderService();
        ReflectionTestUtils.setField(orderService, "orderDao", orderDao);
        ReflectionTestUtils.setField(orderService, "redisService", redisService);
        ReflectionTestUtils.setField(orderService, "deliveryAddressDao", deliveryAddressDao);
        user = new SeckillUser();
        user.setId(7L);
        goodsVo = new GoodsVo();
        goodsVo.setId(9L);
        goodsVo.setGoodsName("iPhone 17");
        goodsVo.setSeckillPrice(5999.0);
    }

    @Test
    void createCacheOrderInsertsBothOrdersAndWritesCache() {
        stubDefaultAddress();
        stubOrderIdBackfill(55L);

        OrderInfo order = orderService.createCacheOrder(user, goodsVo, null);

        assertEquals(Long.valueOf(55L), order.getId());
        assertEquals("iPhone 17", order.getGoodsName());
        ArgumentCaptor<SeckillOrder> captor = ArgumentCaptor.forClass(SeckillOrder.class);
        verify(orderDao).insertSeckillOrder(captor.capture());
        assertEquals(Long.valueOf(55L), captor.getValue().getOrderId());
        assertEquals(Long.valueOf(7L), captor.getValue().getUserId());
        assertEquals(Long.valueOf(9L), captor.getValue().getGoodsId());
        // 单元测试中无活动事务，缓存立即回写
        verify(redisService).set(eq(OrderKey.getSeckillOrderByUidAndGid), eq("7_9"), any(SeckillOrder.class));
    }

    @Test
    void createOrderWithoutCacheInsertsBothOrdersWithoutCacheWrite() {
        stubDefaultAddress();
        stubOrderIdBackfill(55L);

        OrderInfo order = orderService.createOrderWithoutCache(user, goodsVo, null);

        assertEquals(Long.valueOf(55L), order.getId());
        verify(orderDao).insertSeckillOrder(any(SeckillOrder.class));
        verify(redisService, never()).set(any(), any(), any());
    }

    @Test
    void createOrderFailsFastWhenDeliveryAddressMissing() {
        when(deliveryAddressDao.selectDefault(7L)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> orderService.createCacheOrder(user, goodsVo, null));

        verify(orderDao, never()).insert(any(OrderInfo.class));
        verify(orderDao, never()).insertSeckillOrder(any(SeckillOrder.class));
    }

    private void stubDefaultAddress() {
        DeliveryAddress address = new DeliveryAddress();
        address.setId(3L);
        address.setReceiverName("张三");
        address.setReceiverMobile("13800000000");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园 1 号");
        when(deliveryAddressDao.selectDefault(7L)).thenReturn(address);
    }

    /** 模拟 MyBatis-Plus insert 后回填自增主键。 */
    private void stubOrderIdBackfill(long orderId) {
        when(orderDao.insert(any(OrderInfo.class))).thenAnswer(invocation -> {
            invocation.<OrderInfo>getArgument(0).setId(orderId);
            return 1;
        });
    }
}

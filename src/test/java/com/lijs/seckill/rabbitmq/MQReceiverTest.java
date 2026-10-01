package com.lijs.seckill.rabbitmq;

import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.GoodsKey;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.service.GoodsService;
import com.lijs.seckill.service.SeckillService;
import com.lijs.seckill.vo.GoodsVo;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MQReceiverTest {

    @Mock
    private GoodsService goodsService;
    @Mock
    private SeckillService seckillService;
    @Mock
    private RedisService redisService;
    @Mock
    private Channel channel;

    private MQReceiver receiver;
    private SeckillMessage message;
    private String payload;
    private GoodsVo goodsVo;

    @BeforeEach
    void setUp() {
        receiver = new MQReceiver();
        ReflectionTestUtils.setField(receiver, "goodsService", goodsService);
        ReflectionTestUtils.setField(receiver, "seckillService", seckillService);
        ReflectionTestUtils.setField(receiver, "redisService", redisService);

        SeckillUser user = new SeckillUser();
        user.setId(7L);
        message = new SeckillMessage();
        message.setUser(user);
        message.setGoodsId(9L);
        message.setReservationId("res-1");
        payload = RedisService.beanToString(message);
        goodsVo = new GoodsVo();
        when(goodsService.getGoodsVoByGoodsId(9L)).thenReturn(goodsVo);
    }

    @Test
    void successfulOrderMarksReservationAndAcknowledges() throws Exception {
        when(seckillService.seckillWithCache(any(SeckillUser.class),
                eq(goodsVo), isNull(Long.class))).thenReturn(new OrderInfo());
        when(redisService.markReservationCommitted("res-1")).thenReturn(true);

        receiver.receiveSeckill(payload, channel, 11L);

        verify(redisService).markReservationCommitted("res-1");
        verify(redisService, never()).rollbackStockOnce(GoodsKey.getSeckillGoodsStock, "9", "res-1");
        verify(channel).basicAck(11L, false);
    }

    @Test
    void ackFailureDoesNotRestoreStock() throws Exception {
        when(seckillService.seckillWithCache(any(SeckillUser.class),
                eq(goodsVo), isNull(Long.class))).thenReturn(new OrderInfo());
        when(redisService.markReservationCommitted("res-1")).thenReturn(true);
        doThrow(new IOException("channel closed")).when(channel).basicAck(11L, false);

        receiver.receiveSeckill(payload, channel, 11L);

        verify(redisService, never()).rollbackStockOnce(GoodsKey.getSeckillGoodsStock, "9", "res-1");
        verify(channel, never()).basicNack(11L, false, false);
    }

    @Test
    void stockExhaustedRestoresOnceAndAcknowledges() throws Exception {
        when(seckillService.seckillWithCache(any(SeckillUser.class),
                eq(goodsVo), isNull(Long.class))).thenReturn(null);

        receiver.receiveSeckill(payload, channel, 11L);

        verify(redisService).rollbackStockOnce(GoodsKey.getSeckillGoodsStock, "9", "res-1");
        verify(channel).basicAck(11L, false);
    }

    @Test
    void duplicateOrderAfterAckFailureDoesNotRestoreCommittedReservation() throws Exception {
        when(seckillService.seckillWithCache(any(SeckillUser.class),
                eq(goodsVo), isNull(Long.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
        when(redisService.isReservationCommitted("res-1")).thenReturn(true);

        receiver.receiveSeckill(payload, channel, 12L);

        verify(redisService).isReservationCommitted("res-1");
        verify(redisService, never()).rollbackStockOnce(GoodsKey.getSeckillGoodsStock, "9", "res-1");
        verify(channel).basicAck(12L, false);
    }

    @Test
    void duplicateOrderForUncommittedReservationRestoresAndAcknowledges() throws Exception {
        when(seckillService.seckillWithCache(any(SeckillUser.class),
                eq(goodsVo), isNull(Long.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
        when(redisService.isReservationCommitted("res-1")).thenReturn(false);

        receiver.receiveSeckill(payload, channel, 12L);

        verify(redisService).rollbackStockOnce(GoodsKey.getSeckillGoodsStock, "9", "res-1");
        verify(channel).basicAck(12L, false);
    }

    @Test
    void databaseFailureRestoresAndDeadLettersMessage() throws Exception {
        when(seckillService.seckillWithCache(any(SeckillUser.class),
                eq(goodsVo), isNull(Long.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        receiver.receiveSeckill(payload, channel, 13L);

        verify(redisService).rollbackStockOnce(GoodsKey.getSeckillGoodsStock, "9", "res-1");
        verify(channel).basicNack(13L, false, false);
        verify(channel, never()).basicAck(13L, false);
    }
}

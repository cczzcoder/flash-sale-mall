package com.lijs.seckill.service;

import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.redis.SeckillKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerifyCodeServiceTest {

    @Mock
    private RedisService redisService;

    private VerifyCodeService verifyCodeService;
    private SeckillUser user;

    @BeforeEach
    void setUp() {
        verifyCodeService = new VerifyCodeService();
        ReflectionTestUtils.setField(verifyCodeService, "redisService", redisService);
        user = new SeckillUser();
        user.setId(10001L);
    }

    @Test
    void calculatesVerificationExpressionWithJavaArithmeticPrecedence() {
        assertEquals(7, VerifyCodeService.calc("1+2*3"));
        assertEquals(1, VerifyCodeService.calc("9-4*2"));
        assertEquals(-2, VerifyCodeService.calc("2*3-8"));
    }

    @Test
    void rejectsUnexpectedVerificationExpression() {
        assertThrows(IllegalArgumentException.class, () -> VerifyCodeService.calc("1/2"));
        assertThrows(IllegalArgumentException.class, () -> VerifyCodeService.calc("1+"));
    }

    @Test
    void checkVCodeReturnsTrueAndConsumesAnswerOnMatch() {
        when(redisService.get(SeckillKey.getSeckillVerifyCode, "10001_1", Integer.class)).thenReturn(7);

        assertTrue(verifyCodeService.checkVCode(user, 1L, 7));

        verify(redisService).delete(SeckillKey.getSeckillVerifyCode, "10001_1");
    }

    @Test
    void checkVCodeReturnsFalseAndKeepsAnswerOnMismatch() {
        when(redisService.get(SeckillKey.getSeckillVerifyCode, "10001_1", Integer.class)).thenReturn(7);

        assertFalse(verifyCodeService.checkVCode(user, 1L, 8));

        verify(redisService, never()).delete(SeckillKey.getSeckillVerifyCode, "10001_1");
    }

    @Test
    void checkVCodeReturnsFalseWhenAnswerExpired() {
        when(redisService.get(SeckillKey.getSeckillVerifyCode, "10001_1", Integer.class)).thenReturn(null);

        assertFalse(verifyCodeService.checkVCode(user, 1L, 7));

        verify(redisService, never()).delete(SeckillKey.getSeckillVerifyCode, "10001_1");
    }

    @Test
    void createSeckillVerifyCodeRejectsInvalidInput() {
        assertNull(verifyCodeService.createSeckillVerifyCode(null, 1L));

        verifyNoInteractions(redisService);
    }

    @Test
    void createSeckillVerifyCodeDrawsImageAndStoresAnswer() {
        BufferedImage image = verifyCodeService.createSeckillVerifyCode(user, 1L);

        assertEquals(80, image.getWidth());
        assertEquals(30, image.getHeight());
        ArgumentCaptor<Object> answer = ArgumentCaptor.forClass(Object.class);
        verify(redisService).set(eq(SeckillKey.getSeckillVerifyCode), eq("10001_1"), answer.capture());
        assertTrue(answer.getValue() instanceof Integer);
    }
}

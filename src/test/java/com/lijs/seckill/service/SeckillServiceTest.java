package com.lijs.seckill.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeckillServiceTest {

    @Test
    void calculatesVerificationExpressionWithJavaArithmeticPrecedence() {
        assertEquals(7, SeckillService.calc("1+2*3"));
        assertEquals(1, SeckillService.calc("9-4*2"));
        assertEquals(-2, SeckillService.calc("2*3-8"));
    }

    @Test
    void rejectsUnexpectedVerificationExpression() {
        assertThrows(IllegalArgumentException.class, () -> SeckillService.calc("1/2"));
        assertThrows(IllegalArgumentException.class, () -> SeckillService.calc("1+"));
    }
}

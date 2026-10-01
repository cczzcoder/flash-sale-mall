package com.lijs.seckill.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeControllerTest {

    @Test
    void rootRedirectsToGoodsList() {
        assertEquals("redirect:/goods/list", new HomeController().home());
    }
}

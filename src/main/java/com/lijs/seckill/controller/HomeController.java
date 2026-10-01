package com.lijs.seckill.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Public entry point for the demo storefront. */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/goods/list";
    }
}

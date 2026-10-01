package com.lijs.seckill.controller;

import com.lijs.seckill.result.Result;
import com.lijs.seckill.service.SeckillUserService;
import com.lijs.seckill.vo.LoginVo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

/** Test-only login endpoint. It is not registered outside the loadtest profile. */
@Profile("loadtest")
@RequestMapping("/login")
@Controller
public class LoadTestLoginController {

    private final SeckillUserService seckillUserService;

    public LoadTestLoginController(SeckillUserService seckillUserService) {
        this.seckillUserService = seckillUserService;
    }

    @RequestMapping("/token_test")
    @ResponseBody
    public Result<String> doLoginTest(HttpServletResponse response, @Valid LoginVo loginVo) {
        return Result.success(seckillUserService.loginTest(response, loginVo));
    }
}

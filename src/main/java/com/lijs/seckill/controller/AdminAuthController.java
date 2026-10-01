package com.lijs.seckill.controller;

import com.lijs.seckill.access.AccessLimit;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.AdminAuthService;
import com.lijs.seckill.vo.AdminLoginVo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.validation.Valid;

@Controller
@RequestMapping("/admin/auth")
public class AdminAuthController {
    private final AdminAuthService authService;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @RequestMapping("/login")
    @ResponseBody
    @AccessLimit(seconds = 60, maxCount = 5, needLogin = false)
    public Result<String> login(@Valid AdminLoginVo loginVo) {
        String token = authService.login(loginVo);
        return token == null ? Result.error(ResultCode.ADMIN_AUTH_FAILED) : Result.success(token);
    }

    @RequestMapping("/logout")
    @ResponseBody
    public Result<Boolean> logout(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        authService.logout(token);
        return Result.success(true);
    }
}

package com.lijs.seckill.controller;

import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.AdminAuthService;
import com.lijs.seckill.vo.UserInfoVo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {
    private final AdminAuthService authService;

    public AdminUserController(AdminAuthService authService) {
        this.authService = authService;
    }

    @RequestMapping("/list")
    @ResponseBody
    public Result<List<UserInfoVo>> list(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        if (!authService.isValid(token)) return Result.error(ResultCode.ADMIN_AUTH_FAILED);
        return Result.success(authService.listUsers(Math.max(1, Math.min(limit, 500))));
    }
}

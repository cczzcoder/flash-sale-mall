package com.lijs.seckill.controller;

import com.lijs.seckill.domain.Shop;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.AdminAuthService;
import com.lijs.seckill.service.ShopService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/** 平台端店铺管理：列表 + 审核通过/停用（复用 X-Admin-Token 鉴权）。 */
@Controller
@RequestMapping("/admin/shops")
public class AdminShopController {

    private final AdminAuthService authService;
    private final ShopService shopService;

    public AdminShopController(AdminAuthService authService, ShopService shopService) {
        this.authService = authService;
        this.shopService = shopService;
    }

    @RequestMapping("/list")
    @ResponseBody
    public Result<List<Shop>> list(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!authService.isValid(token)) {
            return Result.error(ResultCode.ADMIN_AUTH_FAILED);
        }
        return Result.success(shopService.listAll());
    }

    /** status: 1=通过/启用，2=停用。 */
    @RequestMapping(value = "/review", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> review(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                  @RequestParam("shopId") long shopId,
                                  @RequestParam("status") int status) {
        if (!authService.isValid(token)) {
            return Result.error(ResultCode.ADMIN_AUTH_FAILED);
        }
        ResultCode result = shopService.review(shopId, status);
        return result.getCode() == 0 ? Result.success(true) : Result.error(result);
    }
}

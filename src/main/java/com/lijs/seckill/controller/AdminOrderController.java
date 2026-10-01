package com.lijs.seckill.controller;

import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.dao.OrderDao;
import com.lijs.seckill.service.AdminAuthService;
import com.lijs.seckill.service.OrderLifecycleService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {
    private final AdminAuthService authService;
    private final OrderDao orderDao;
    private final OrderLifecycleService lifecycleService;

    public AdminOrderController(AdminAuthService authService, OrderDao orderDao,
                                OrderLifecycleService lifecycleService) {
        this.authService = authService;
        this.orderDao = orderDao;
        this.lifecycleService = lifecycleService;
    }

    @RequestMapping("/list")
    @ResponseBody
    public Result<List<OrderInfo>> list(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                        @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (!authService.isValid(token)) return Result.error(ResultCode.ADMIN_AUTH_FAILED);
        return Result.success(orderDao.selectRecent(Math.max(1, Math.min(limit, 200))));
    }

    @RequestMapping(value = "/ship", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> ship(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                @RequestParam("orderId") long orderId,
                                @RequestParam("shippingCompany") String shippingCompany,
                                @RequestParam("trackingNumber") String trackingNumber) {
        if (!authService.isValid(token)) return Result.error(ResultCode.ADMIN_AUTH_FAILED);
        return lifecycleService.shipPaidOrder(orderId, shippingCompany, trackingNumber)
                ? Result.success(true) : Result.error(ResultCode.ORDER_STATE_INVALID);
    }

    @RequestMapping(value = "/refund/approve", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> approveRefund(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                         @RequestParam("orderId") long orderId) {
        if (!authService.isValid(token)) return Result.error(ResultCode.ADMIN_AUTH_FAILED);
        return lifecycleService.approveRefund(orderId)
                ? Result.success(true) : Result.error(ResultCode.ORDER_STATE_INVALID);
    }
}

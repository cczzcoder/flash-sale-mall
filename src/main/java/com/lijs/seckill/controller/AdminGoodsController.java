package com.lijs.seckill.controller;

import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.AdminAuthService;
import com.lijs.seckill.service.AdminGoodsService;
import com.lijs.seckill.vo.AdminGoodsVo;
import com.lijs.seckill.vo.GoodsVo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.validation.Valid;
import java.util.List;

@Controller
@RequestMapping("/admin/goods")
public class AdminGoodsController {
    private final AdminAuthService authService;
    private final AdminGoodsService goodsService;

    public AdminGoodsController(AdminAuthService authService, AdminGoodsService goodsService) {
        this.authService = authService;
        this.goodsService = goodsService;
    }

    @RequestMapping("/list")
    @ResponseBody
    public Result<List<GoodsVo>> list(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        return !authService.isValid(token) ? Result.error(ResultCode.ADMIN_AUTH_FAILED)
                : Result.success(goodsService.list());
    }

    @RequestMapping(value = "/save", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> save(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                @Valid AdminGoodsVo vo) {
        if (!authService.isValid(token)) return Result.error(ResultCode.ADMIN_AUTH_FAILED);
        ResultCode result = goodsService.save(vo);
        return result.getCode() == 0 ? Result.success(true) : Result.error(result);
    }

    @RequestMapping(value = "/restock", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> restock(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                   @RequestParam("goodsId") long goodsId,
                                   @RequestParam("count") int count) {
        if (!authService.isValid(token)) return Result.error(ResultCode.ADMIN_AUTH_FAILED);
        ResultCode result = goodsService.restock(goodsId, count);
        return result.getCode() == 0 ? Result.success(true) : Result.error(result);
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> delete(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                  @RequestParam("goodsId") long goodsId) {
        if (!authService.isValid(token)) return Result.error(ResultCode.ADMIN_AUTH_FAILED);
        ResultCode result = goodsService.delete(goodsId);
        return result.getCode() == 0 ? Result.success(true) : Result.error(result);
    }
}

package com.lijs.seckill.controller;

import com.lijs.seckill.domain.DeliveryAddress;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.DeliveryAddressService;
import com.lijs.seckill.vo.DeliveryAddressVo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.validation.Valid;
import java.util.List;

@Controller
@RequestMapping("/address")
public class DeliveryAddressController {
    private final DeliveryAddressService addressService;

    public DeliveryAddressController(DeliveryAddressService addressService) {
        this.addressService = addressService;
    }

    @RequestMapping("/list")
    @ResponseBody
    public Result<List<DeliveryAddress>> list(SeckillUser user) {
        return user == null ? Result.error(ResultCode.SESSION_ERROR)
                : Result.success(addressService.list(user.getId()));
    }

    @RequestMapping(value = "/save", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> save(SeckillUser user, @Valid DeliveryAddressVo address) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        ResultCode result = addressService.save(user.getId(), address);
        return result.getCode() == 0 ? Result.success(true) : Result.error(result);
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> delete(SeckillUser user, @RequestParam("addressId") long addressId) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        ResultCode result = addressService.delete(addressId, user.getId());
        return result.getCode() == 0 ? Result.success(true) : Result.error(result);
    }
}

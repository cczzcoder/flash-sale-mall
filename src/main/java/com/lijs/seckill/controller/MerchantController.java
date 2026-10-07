package com.lijs.seckill.controller;

import com.lijs.seckill.access.AccessLimit;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.domain.Shop;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.AdminGoodsService;
import com.lijs.seckill.service.ShopService;
import com.lijs.seckill.vo.AdminGoodsVo;
import com.lijs.seckill.vo.GoodsVo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.validation.Valid;
import java.util.List;

/**
 * 商家工作台接口（页面：/merchant.htm）。
 *
 * <p>鉴权：依赖用户侧分布式 Session（token Cookie），并强制校验：
 * <ol>
 *   <li>已登录；</li>
 *   <li>已入驻且店铺状态为「营业中」；</li>
 *   <li>商品归属校验在 {@link AdminGoodsService} 内以 scopeShopId 参数强制实施，
 *       商家无法通过改 goodsId 操作他人商品（防越权）。</li>
 * </ol>
 */
@Controller
@RequestMapping("/merchant")
public class MerchantController {

    private final ShopService shopService;
    private final AdminGoodsService goodsService;

    public MerchantController(ShopService shopService, AdminGoodsService goodsService) {
        this.shopService = shopService;
        this.goodsService = goodsService;
    }

    /** 提交入驻申请（任何登录用户都可提交，重复提交会被拒绝）。 */
    @RequestMapping(value = "/apply", method = RequestMethod.POST)
    @ResponseBody
    @AccessLimit(seconds = 60, maxCount = 10)
    public Result<Boolean> apply(SeckillUser user,
                                 @RequestParam("name") String name,
                                 @RequestParam(value = "logo", required = false) String logo,
                                 @RequestParam(value = "description", required = false) String description) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        ResultCode result = shopService.apply(user.getId(), name, logo, description);
        return result.getCode() == 0 ? Result.success(true) : Result.error(result);
    }

    /** 我的店铺状态：null 表示未入驻；页面据此渲染申请表单或工作台。 */
    @RequestMapping("/shop")
    @ResponseBody
    public Result<Shop> shop(SeckillUser user) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        return Result.success(shopService.getByOwner(user.getId()));
    }

    /** 我的商品列表（仅营业中的店铺可访问）。 */
    @RequestMapping("/goods/list")
    @ResponseBody
    public Result<List<GoodsVo>> goodsList(SeckillUser user) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        Shop shop = shopService.getByOwner(user.getId());
        if (shop == null) {
            return Result.error(ResultCode.SHOP_NOT_APPLIED);
        }
        if (shop.getStatus() != Shop.STATUS_ACTIVE) {
            return Result.error(ResultCode.SHOP_NOT_ACTIVE);
        }
        return Result.success(goodsService.listByShop(shop.getId()));
    }

    /** 新增/编辑商品，服务端强制拼接 shopId 做归属校验。 */
    @RequestMapping(value = "/goods/save", method = RequestMethod.POST)
    @ResponseBody
    @AccessLimit(seconds = 60, maxCount = 30)
    public Result<Boolean> goodsSave(SeckillUser user, @Valid AdminGoodsVo vo) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        Shop shop = shopService.getByOwner(user.getId());
        if (shop == null) {
            return Result.error(ResultCode.SHOP_NOT_APPLIED);
        }
        if (shop.getStatus() != Shop.STATUS_ACTIVE) {
            return Result.error(ResultCode.SHOP_NOT_ACTIVE);
        }
        ResultCode result = goodsService.save(vo, shop.getId());
        return result.getCode() == 0 ? Result.success(true) : Result.error(result);
    }

    /** 补货（库存编辑唯一入口，避免覆盖 Redis 预扣量）。 */
    @RequestMapping(value = "/goods/restock", method = RequestMethod.POST)
    @ResponseBody
    @AccessLimit(seconds = 60, maxCount = 30)
    public Result<Boolean> goodsRestock(SeckillUser user,
                                        @RequestParam("goodsId") long goodsId,
                                        @RequestParam("count") int count) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        Shop shop = shopService.getByOwner(user.getId());
        if (shop == null) {
            return Result.error(ResultCode.SHOP_NOT_APPLIED);
        }
        if (shop.getStatus() != Shop.STATUS_ACTIVE) {
            return Result.error(ResultCode.SHOP_NOT_ACTIVE);
        }
        ResultCode result = goodsService.restock(goodsId, count, shop.getId());
        return result.getCode() == 0 ? Result.success(true) : Result.error(result);
    }

    /** 删除商品（已有订单的商品不可删）。 */
    @RequestMapping(value = "/goods/delete", method = RequestMethod.POST)
    @ResponseBody
    @AccessLimit(seconds = 60, maxCount = 30)
    public Result<Boolean> goodsDelete(SeckillUser user, @RequestParam("goodsId") long goodsId) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        Shop shop = shopService.getByOwner(user.getId());
        if (shop == null) {
            return Result.error(ResultCode.SHOP_NOT_APPLIED);
        }
        if (shop.getStatus() != Shop.STATUS_ACTIVE) {
            return Result.error(ResultCode.SHOP_NOT_ACTIVE);
        }
        ResultCode result = goodsService.delete(goodsId, shop.getId());
        return result.getCode() == 0 ? Result.success(true) : Result.error(result);
    }
}

package com.lijs.seckill.controller;

import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.domain.OrderStatus;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.GoodsService;
import com.lijs.seckill.service.OrderService;
import com.lijs.seckill.service.OrderLifecycleService;
import com.lijs.seckill.vo.GoodsVo;
import com.lijs.seckill.vo.OrderDetailVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

/**
 * 订单控制器，提供订单详情查询接口。
 *
 * <p>配合前端"秒杀结果轮询"使用：用户在 {@link SeckillController#result} 轮询到
 * orderId（> 0）后，再调用此接口获取订单+商品的完整展示信息。
 */
@RequestMapping("/order")
@Controller
public class OrderController {

    @Autowired
    private GoodsService goodsService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderLifecycleService orderLifecycleService;

    @RequestMapping("/list")
    @ResponseBody
    public Result<List<OrderInfo>> list(SeckillUser user,
                                        @RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        return Result.success(orderService.listByUserId(user.getId(), boundedLimit));
    }

    @RequestMapping(value = "/cancel", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> cancel(SeckillUser user, @RequestParam("orderId") long orderId) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        OrderInfo order = orderService.getOrderByOrderId(orderId);
        if (order == null) {
            return Result.error(ResultCode.ORDER_NOT_EXIST);
        }
        if (order.getUserId() == null || !order.getUserId().equals(user.getId())) {
            return Result.error(ResultCode.ORDER_FORBIDDEN);
        }
        if (!OrderStatus.UNPAID.getCodeEquals(order.getOrderStatus())) {
            return Result.error(ResultCode.ORDER_NOT_CANCELLABLE);
        }
        return orderLifecycleService.cancelUnpaidOrder(orderId, user.getId())
                ? Result.success(true) : Result.error(ResultCode.ORDER_NOT_CANCELLABLE);
    }

    @RequestMapping(value = "/receive", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> receive(SeckillUser user, @RequestParam("orderId") long orderId) {
        if (user == null) return Result.error(ResultCode.SESSION_ERROR);
        return orderLifecycleService.receiveShippedOrder(orderId, user.getId())
                ? Result.success(true) : Result.error(ResultCode.ORDER_STATE_INVALID);
    }

    @RequestMapping(value = "/complete", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> complete(SeckillUser user, @RequestParam("orderId") long orderId) {
        if (user == null) return Result.error(ResultCode.SESSION_ERROR);
        return orderLifecycleService.completeReceivedOrder(orderId, user.getId())
                ? Result.success(true) : Result.error(ResultCode.ORDER_STATE_INVALID);
    }

    @RequestMapping(value = "/refund", method = RequestMethod.POST)
    @ResponseBody
    public Result<Boolean> refund(SeckillUser user, @RequestParam("orderId") long orderId,
                                  @RequestParam("reason") String reason) {
        if (user == null) return Result.error(ResultCode.SESSION_ERROR);
        return orderLifecycleService.requestRefund(orderId, user.getId(), reason)
                ? Result.success(true) : Result.error(ResultCode.ORDER_STATE_INVALID);
    }

    /**
     * 查询订单详情（GET /order/detail?orderId=xxx）。
     *
     * <p>校验逻辑：
     * <ol>
     *   <li>用户未登录（token 失效）→ 返回 SESSION_ERROR</li>
     *   <li>订单不存在（orderId 错误）→ 返回 ORDER_NOT_EXIST</li>
     *   <li>订单存在 → 组装订单信息 + 商品信息一并返回</li>
     * </ol>
     *
     * @param user    由 UserArgumentResolver 自动注入的当前用户
     * @param orderId 订单 ID（URL 参数）
     * @return 包含 {@link OrderDetailVo}（订单信息 + 商品信息）的统一响应
     */
    @RequestMapping("/detail")
    @ResponseBody
    public Result<OrderDetailVo> info(SeckillUser user, @RequestParam("orderId") long orderId) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        OrderInfo order = orderService.getOrderByOrderId(orderId);
        if (order == null) {
            return Result.error(ResultCode.ORDER_NOT_EXIST);
        }
        // 订单详情属于私有数据，登录并不代表可以读取其他用户订单
        if (order.getUserId() == null || !order.getUserId().equals(user.getId())) {
            return Result.error(ResultCode.ORDER_FORBIDDEN);
        }
        // 根据订单中记录的商品 ID查询商品详情，合并到 VO 一次返回
        long goodsId = order.getGoodsId();
        GoodsVo gVo = goodsService.getGoodsVoByGoodsId(goodsId);
        OrderDetailVo oVo = new OrderDetailVo();
        oVo.setGoodsVo(gVo);
        oVo.setOrder(order);
        return Result.success(oVo);
    }
}

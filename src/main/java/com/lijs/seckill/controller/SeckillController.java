package com.lijs.seckill.controller;

import com.lijs.seckill.access.AccessLimit;
import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.domain.SeckillOrder;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.rabbitmq.MQSender;
import com.lijs.seckill.rabbitmq.SeckillMessage;
import com.lijs.seckill.redis.GoodsKey;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.redis.SeckillKey;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.GoodsService;
import com.lijs.seckill.service.DeliveryAddressService;
import com.lijs.seckill.service.OrderService;
import com.lijs.seckill.service.SeckillService;
import com.lijs.seckill.service.VerifyCodeService;
import com.lijs.seckill.util.UUIDUtil;
import com.lijs.seckill.vo.GoodsVo;
import com.lijs.seckill.vo.SeckillWindowVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

@RequestMapping("/seckill")
@Controller
public class SeckillController implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(SeckillController.class);

    @Autowired
    private GoodsService goodsService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private SeckillService seckillService;
    @Autowired
    private VerifyCodeService verifyCodeService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private DeliveryAddressService deliveryAddressService;
    @Autowired
    private MQSender mQSender;

    /**
     * 系统初始化的时候做的事情。
     * 在容器启动时候，检测到了实现了接口InitializingBean之后，
     */
    @Override
    public void afterPropertiesSet() {
        List<GoodsVo> goodslist = goodsService.getGoodsVoList();
        if (goodslist == null) {
            return;
        }
        for (GoodsVo goods : goodslist) {
            // 只在 key 缺失时预热，避免重启覆盖 Redis 中已预扣的库存。
            redisService.setIfAbsent(GoodsKey.getSeckillGoodsStock, "" + goods.getId(), goods.getStockCount());
            redisService.set(SeckillKey.getSeckillWindow, "" + goods.getId(),
                    new SeckillWindowVo(goods.getStartDate(), goods.getEndDate()));
        }
        logger.info("缓存加载完成...");
    }

    /**
     * 生成图片验证码
     *
     * @param model    模型
     * @param user     秒杀用户
     * @param goodsId  商品ID
     * @param response HTTP响应
     * @return 验证码结果
     */
    @RequestMapping(value = "/verifyCode")
    @ResponseBody
    @AccessLimit(seconds = 5, maxCount = 5) // 验证码生成占用 CPU 与输出流，同一用户 5 秒内最多 5 次
    public Result<String> verifyCode(Model model, SeckillUser user,
                                     @RequestParam("goodsId") Long goodsId, HttpServletResponse response) {
        model.addAttribute("user", user);
        // 如果用户为空则返回登录页面
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        BufferedImage img = verifyCodeService.createSeckillVerifyCode(user, goodsId);
        try {
            OutputStream out = response.getOutputStream();
            ImageIO.write(img, "JPEG", out);
            out.flush();
            out.close();
            return null;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return Result.error(ResultCode.SECKILL_FAIL);
        }
    }

    /**
     * 获取秒杀路径并验证验证码
     *
     * @param model      模型
     * @param user       秒杀用户
     * @param goodsId    商品ID
     * @param verifyCode 验证码
     * @return 秒杀路径
     */
    @RequestMapping(value = "/getPath")
    @ResponseBody
    @AccessLimit(seconds = 5, maxCount = 5) // 5s 内最多 5 次；拦截器先于本方法执行，脚本高频请求会更早被拒
    public Result<String> getSeckillPath(Model model, SeckillUser user,
                                         @RequestParam("goodsId") Long goodsId,
                                         @RequestParam(value = "verifyCode", defaultValue = "0") int verifyCode) {
        model.addAttribute("user", user);
        // 如果用户为空，则返回至登录页面
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        ResultCode window = checkSeckillWindow(goodsId);
        if (window != ResultCode.SUCCESS) {
            return Result.error(window);
        }
        // 验证验证码
        boolean check = verifyCodeService.checkVCode(user, goodsId, verifyCode);
        if (!check) {
            return Result.error(ResultCode.REQUEST_ILLEGAL);
        }
        logger.info("通过!");
        // 生成一个随机串
        String path = seckillService.createSeckillPath(user, goodsId);
        logger.info("path:{}", path);
        return Result.success(path);
    }

    /**
     * 轮询查看秒杀结果
     * 秒杀成功，返回订单的Id。
     * 库存不足直接返回-1。
     * 排队中则返回0。
     *
     * 前端已按指数退避轮询，此处再加服务端限流兜底：
     * 前端被绕过或脚本刷接口时，避免海量轮询直接压垮 Redis。
     *
     * @param user    秒杀用户
     * @param goodsId 商品ID
     * @return 秒杀结果
     */
    @RequestMapping(value = "/result", method = RequestMethod.GET)
    @ResponseBody
    @AccessLimit(seconds = 5, maxCount = 10) // 退避策略下正常用户远达不到，异常刷接口才会触发
    public Result<Long> result(SeckillUser user,
                               @RequestParam(value = "goodsId", defaultValue = "0") long goodsId) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        long result = seckillService.getSeckillResult(user.getId(), goodsId);
        logger.info("轮询 result:{}", result);
        return Result.success(result);
    }

    /**
     * 秒杀接口：支持【缓存+消息队列】
     *
     * @param model   模型
     * @param user    秒杀用户
     * @param goodsId 商品ID
     * @param path    秒杀路径
     * @return 秒杀结果
     */
    @RequestMapping(value = "/{path}/seckillWithCacheAndMQ", method = RequestMethod.POST)
    @ResponseBody
    public Result<Integer> seckillWithCacheAndMQ(Model model, SeckillUser user,
                                                 @RequestParam(value = "goodsId", defaultValue = "0") long goodsId,
                                                 @RequestParam(value = "deliveryAddrId", required = false) Long deliveryAddrId,
                                                 @PathVariable("path") String path) {
        model.addAttribute("user", user);
        // 1.如果用户为空则返回至登录页面
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        ResultCode window = checkSeckillWindow(goodsId);
        if (window != ResultCode.SUCCESS) {
            return Result.error(window);
        }
        // 2.验证秒杀path路径
        if (!deliveryAddressService.existsForOrder(user.getId(), deliveryAddrId)) {
            return Result.error(deliveryAddrId == null ? ResultCode.ADDRESS_REQUIRED : ResultCode.ADDRESS_NOT_EXIST);
        }
        boolean check = seckillService.checkPath(user, goodsId, path);
        if (!check) {
            return Result.error(ResultCode.REQUEST_ILLEGAL);
        }
        // 3.重复下单校验：必须放在预扣库存之前。
        //   否则重复请求会扣掉 Redis 库存却直接 return，库存永久泄漏。
        //   走缓存而非查库，热路径不打 DB；最终兜底是 seckill_order 的唯一索引 u_uid_gid。
        SeckillOrder order = orderService.getSeckillOrderByUserIdAndGoodsIdCache(user.getId(), goodsId);
        if (order != null) { // 重复下单
            return Result.error(ResultCode.REPEAT_SECKILL);
        }
        // 4.Redis 原子预扣库存：DECR 后若结果 < 0 自动 INCR 回滚，防止库存永久为负
        boolean stockAvailable = redisService.preDecrStock(GoodsKey.getSeckillGoodsStock, "" + goodsId);
        if (!stockAvailable) {
            return Result.error(ResultCode.SECKILL_OVER_ERROR);
        }
        // 5.正常请求入队；捕获 AmqpException（TCP 重试耗尽）时补偿 Redis 库存
        SeckillMessage mms = new SeckillMessage();
        mms.setUser(user);
        mms.setGoodsId(goodsId);
        mms.setDeliveryAddrId(deliveryAddrId);
        mms.setReservationId(UUIDUtil.uuid());
        try {
            mQSender.sendSeckillMessage(mms);
        } catch (AmqpException e) {
            // TCP 重试耗尽仍失败：Redis 已预扣但消息未入队，立即补偿
            redisService.rollbackStockOnce(GoodsKey.getSeckillGoodsStock, "" + goodsId,
                    mms.getReservationId());
            logger.error("MQ 入队失败，Redis 库存已回滚 goodsId={}", goodsId, e);
            return Result.error(ResultCode.SECKILL_FAIL);
        }
        // 返回0代表排队中
        return Result.success(0);
    }

    /**
     * 1000*10
     * QPS 703.4822370735138
     * 秒杀接口：未支持【缓存+消息队列】
     * 秒杀操作，直接返回订单详情页
     *
     * @param model   模型
     * @param user    秒杀用户
     * @param goodsId 商品ID
     * @return 订单详情页
     */
    @RequestMapping("/seckillWithoutCache")
    public String seckillWithoutCache(Model model, SeckillUser user, @RequestParam("goodsId") Long goodsId,
                                      @RequestParam(value = "deliveryAddrId", required = false) Long deliveryAddrId) {
        model.addAttribute("user", user);
        // 如果用户为空则返回至登录页面
        if (user == null) {
            return "login";
        }
        ResultCode window = checkSeckillWindow(goodsId);
        if (window != ResultCode.SUCCESS) {
            model.addAttribute("errorMessage", window);
            return "seckill_fail";
        }
        if (!deliveryAddressService.existsForOrder(user.getId(), deliveryAddrId)) {
            model.addAttribute("errorMessage", deliveryAddrId == null
                    ? ResultCode.ADDRESS_REQUIRED : ResultCode.ADDRESS_NOT_EXIST);
            return "seckill_fail";
        }
        GoodsVo goodsVo = goodsService.getGoodsVoByGoodsId(goodsId);
        if (goodsVo == null) {
            model.addAttribute("errorMessage", ResultCode.GOODS_NOT_EXIST);
            return "seckill_fail";
        }
        // 判断商品库存，库存大于0，才进行操作，多线程下会出错
        int stockCount = goodsVo.getStockCount();
        if (stockCount <= 0) {
            model.addAttribute("errorMessage", ResultCode.SECKILL_OVER_ERROR);
            return "seckill_fail";
        }
        // 判断这个秒杀订单形成没有，判断是否已经秒杀到了，避免一个账户秒杀多个商品
        SeckillOrder order = orderService.getSeckillOrderByUserIdAndGoodsId(user.getId(), goodsId);
        if (order != null) {//重复下单
            model.addAttribute("errorMessage", ResultCode.REPEAT_SECKILL);
            return "seckill_fail";
        }
        OrderInfo orderinfo = seckillService.seckill(user, goodsVo, deliveryAddrId);
        // 库存已耗尽时 seckill() 返回 null，需单独处理，否则 order_detail 页渲染崩溃
        if (orderinfo == null) {
            model.addAttribute("errorMessage", ResultCode.SECKILL_OVER_ERROR);
            return "seckill_fail";
        }
        // 秒杀成功，跳转到订单详情页
        model.addAttribute("orderinfo", orderinfo);
        model.addAttribute("goods", goodsVo);
        return "order_detail";
    }

    /** 服务端校验活动窗口，前端倒计时只负责展示，不能作为业务依据。 */
    private ResultCode checkSeckillWindow(long goodsId) {
        if (goodsId <= 0) {
            return ResultCode.GOODS_NOT_EXIST;
        }
        SeckillWindowVo cachedWindow = redisService.get(SeckillKey.getSeckillWindow,
                "" + goodsId, SeckillWindowVo.class);
        Date start;
        Date end;
        if (cachedWindow != null) {
            start = cachedWindow.getStartDate();
            end = cachedWindow.getEndDate();
        } else {
            GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
            if (goods == null) {
                return ResultCode.GOODS_NOT_EXIST;
            }
            start = goods.getStartDate();
            end = goods.getEndDate();
            redisService.setIfAbsent(SeckillKey.getSeckillWindow, "" + goodsId,
                    new SeckillWindowVo(start, end));
        }
        if (start == null || end == null || !start.before(end)) {
            return ResultCode.GOODS_TIME_INVALID;
        }
        Date now = new Date();
        if (now.before(start)) {
            return ResultCode.SECKILL_NOT_STARTED;
        }
        if (now.after(end)) {
            return ResultCode.SECKILL_ENDED;
        }
        return ResultCode.SUCCESS;
    }

}

package com.lijs.seckill.controller;

import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.GoodsKey;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.GoodsService;
import com.lijs.seckill.vo.GoodsDetailVo;
import com.lijs.seckill.vo.GoodsVo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 商品控制器，提供商品列表和商品详情的展示接口。
 *
 * <p>本类演示了三种性能递进的页面渲染方案：
 * <ol>
 *   <li><b>无缓存</b>（listWithoutCache）：每次请求都查 DB + 渲染模板，QPS 最低。</li>
 *   <li><b>页面缓存</b>（goodsListWithCache / goodsDetailCache）：将渲染后的 HTML 字符串存入
 *       Redis，命中时直接返回，大幅减少模板渲染和 DB 查询开销，QPS 提升约 50%。
 *       缓存时间不宜过长，否则数据实时性变差。</li>
 *   <li><b>前后端分离静态化</b>（detailStaticPage）：HTML 页面完全静态化，
 *       动态数据通过此 JSON 接口异步拉取，彻底分离渲染压力。</li>
 * </ol>
 */
@RequestMapping("/goods")
@Controller
public class GoodsController {

    private final Logger logger = LoggerFactory.getLogger(GoodsController.class);

    @Autowired
    private GoodsService goodsService;
    @Autowired
    private RedisService redisService;
    /** Spring Boot 自动配置的 Thymeleaf 模板引擎，用于手动渲染模板并缓存结果 */
    @Autowired
    private TemplateEngine templateEngine;
    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 商品列表页（无缓存版，GET /goods/listWithoutCache）。
     * 每次请求都查询数据库并渲染模板，适合开发调试，不适合生产高并发场景。
     * 实测 QPS ≈ 785（1000 并发 × 10 次）。
     */
    @RequestMapping("/listWithoutCache")
    public String listWithoutCache(Model model, SeckillUser user) {
        model.addAttribute("user", user);
        List<GoodsVo> goodsList = goodsService.getGoodsVoList();
        model.addAttribute("goodsList", goodsList);
        return "goods_list";
    }

    /**
     * 商品列表页（页面缓存版，GET /goods/list）。
     *
     * <p>缓存策略：
     * <ol>
     *   <li>优先从 Redis 取已渲染好的 HTML 字符串，命中则直接返回。</li>
     *   <li>未命中时查 DB、渲染模板，将结果存入 Redis（TTL 由 {@link GoodsKey#getGoodsList} 决定，默认 60s）。</li>
     * </ol>
     * 实测 QPS ≈ 1202，较无缓存版提升约 53%。
     *
     * <p>注意：{@code produces = "text/html"} + {@code @ResponseBody} 告诉 Spring MVC
     * 将返回的字符串直接作为响应体输出，而不是当作视图名去查找模板。
     */
    @RequestMapping(value = "/list", produces = "text/html")
    @ResponseBody
    public String goodsListWithCache(Model model, SeckillUser user, HttpServletRequest request,
                                     HttpServletResponse response) {
        // 1. 尝试命中页面缓存
        String html = redisService.get(GoodsKey.getGoodsList, "", String.class);
        if (!StringUtils.isEmpty(html)) {
            return html;
        }
        // 2. 缓存未命中：查询数据，手动渲染模板
        model.addAttribute("user", user);
        List<GoodsVo> goodsList = goodsService.getGoodsVoList();
        model.addAttribute("goodsList", goodsList);
        WebContext context = new WebContext(request, response,
                request.getServletContext(), request.getLocale(), model.asMap());
        html = templateEngine.process("goods_list", context);
        // 3. 将渲染好的 HTML 存入缓存，下次请求直接命中
        if (!StringUtils.isEmpty(html)) {
            redisService.set(GoodsKey.getGoodsList, "", html);
        }
        return html;
    }

    /**
     * 商品详情页（页面缓存版，GET /goods/detail/{goodsId}）。
     *
     * <p>与列表缓存类似，但每个商品有独立的缓存 key（以 goodsId 区分），
     * 保证不同商品的详情页互不干扰。
     * 同时计算秒杀状态（未开始/进行中/已结束）并渲染进 HTML。
     *
     * @param goodsId 商品 ID（路径变量，建议生产环境使用 Snowflake 算法生成）
     * @return 已渲染的 HTML 字符串
     */
    @RequestMapping(value = "/detail/{goodsId}")
    @ResponseBody
    public String goodsDetailCache(Model model, SeckillUser user,
                                   HttpServletRequest request, HttpServletResponse response,
                                   @PathVariable("goodsId") long goodsId) {
        // This page contains the login state, so it must not be shared by goodsId alone.
        model.addAttribute("user", user);
        if (goodsId <= 0) {
            return renderErrorPage(model, user, request, response, ResultCode.GOODS_NOT_EXIST);
        }
        GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
        if (goods == null) {
            return renderErrorPage(model, user, request, response, ResultCode.GOODS_NOT_EXIST);
        }
        if (goods.getStartDate() == null || goods.getEndDate() == null
                || !goods.getStartDate().before(goods.getEndDate())) {
            return renderErrorPage(model, user, request, response, ResultCode.GOODS_TIME_INVALID);
        }
        model.addAttribute("goods", goods);

        long start = goods.getStartDate().getTime();
        long end   = goods.getEndDate().getTime();
        long now   = System.currentTimeMillis();
        int status;           // 0=未开始 1=进行中 2=已结束
        int remainingSeconds; // 距开始的倒计时秒数；进行中=0，已结束=-1
        if (now < start) {
            status = 0;
            remainingSeconds = (int) ((start - now) / 1000);
        } else if (now > end) {
            status = 2;
            remainingSeconds = -1;
        } else {
            status = 1;
            remainingSeconds = 0;
        }
        model.addAttribute("status", status);
        model.addAttribute("remainingSeconds", remainingSeconds);

        // Render per request because the template contains user-specific state.
        WebContext context = new WebContext(request, response,
                request.getServletContext(), request.getLocale(), model.asMap());
        return templateEngine.process("goods_detail", context);
    }

    private String renderErrorPage(Model model, SeckillUser user,
                                   HttpServletRequest request, HttpServletResponse response,
                                   ResultCode resultCode) {
        model.addAttribute("user", user);
        model.addAttribute("errorMessage", resultCode.getMsg());
        WebContext context = new WebContext(request, response,
                request.getServletContext(), request.getLocale(), model.asMap());
        return templateEngine.process("seckill_fail", context);
    }

    /**
     * 商品详情数据接口（前后端分离静态化版，GET /goods/detailStatic/{goodsId}）。
     *
     * <p>页面本身是静态 HTML，不依赖服务端渲染；浏览器加载完页面后，
     * 通过 Ajax 调用此接口获取动态数据（用户信息、商品信息、秒杀状态），
     * 再由前端 JS 填充页面。
     *
     * <p>优势：静态 HTML 可以放 CDN 缓存，服务端只需承载 JSON 接口的压力。
     *
     * @param goodsId 商品 ID（路径变量）
     * @return 包含 {@link GoodsDetailVo}（商品信息 + 秒杀状态 + 用户信息）的统一 JSON 响应
     */
    @RequestMapping(value = "/detailStatic/{goodsId}")
    @ResponseBody
    public Result<GoodsDetailVo> detailStaticPage(Model model, SeckillUser user,
                                                  HttpServletRequest request, HttpServletResponse response,
                                                  @PathVariable("goodsId") long goodsId) {
        logger.info("页面静态化/detail/{goodsId}");

        model.addAttribute("user", user);
        if (goodsId <= 0) {
            return Result.error(ResultCode.GOODS_NOT_EXIST);
        }
        GoodsVo goodsVo = goodsService.getGoodsVoByGoodsId(goodsId);
        if (goodsVo == null) {
            return Result.error(ResultCode.GOODS_NOT_EXIST);
        }
        if (goodsVo.getStartDate() == null || goodsVo.getEndDate() == null
                || !goodsVo.getStartDate().before(goodsVo.getEndDate())) {
            return Result.error(ResultCode.GOODS_TIME_INVALID);
        }
        model.addAttribute("goods", goodsVo);

        long start = goodsVo.getStartDate().getTime();
        long end   = goodsVo.getEndDate().getTime();
        long now   = System.currentTimeMillis();
        int status;
        int remainingSeconds;
        if (now < start) {
            status = 0;
            remainingSeconds = (int) ((start - now) / 1000); // 毫秒转秒
        } else if (now > end) {
            status = 2;
            remainingSeconds = -1;
        } else {
            status = 1;
            remainingSeconds = 0;
        }
        model.addAttribute("status", status);
        model.addAttribute("remainingSeconds", remainingSeconds);

        // 组装 VO，将所有需要的数据一次性返回给前端
        GoodsDetailVo gdVo = new GoodsDetailVo();
        gdVo.setGoodsVo(goodsVo);
        gdVo.setStatus(status);
        gdVo.setremainingSeconds(remainingSeconds);
        gdVo.setUser(user);
        return Result.success(gdVo);
    }
}

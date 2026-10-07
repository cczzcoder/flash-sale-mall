package com.lijs.seckill.controller;

import com.lijs.seckill.access.AccessLimit;
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
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.WebContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private ITemplateEngine templateEngine;
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
        addListModelAttrs(model, goodsService.getGoodsVoList());
        return "goods_list";
    }

    /**
     * 商品列表页（页面缓存版，GET /goods/list）。
     *
     * <p>缓存策略：
     * <ol>
     *   <li>优先从 Redis 取已渲染好的 HTML 字符串，命中则直接返回。</li>
     *   <li>未命中时查 DB、渲染模板，将结果存入 Redis（TTL 由 {@link GoodsKey#getGoodsList} 决定，默认 60s）。</li>
     *   <li>重建时用 SETNX 互斥锁防<b>缓存击穿</b>：只放一个请求去查库渲染，
     *       其余请求 sleep 100ms 后重试读缓存，避免 TTL 到期瞬间所有请求同时砸向 DB。</li>
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
        // 2. 缓存击穿防护：SETNX 抢重建锁（TTL 5s），只有抢到的请求负责查库渲染
        boolean locked = redisService.setIfAbsent(GoodsKey.getGoodsListRebuildLock, "", "1");
        if (!locked) {
            // 没抢到锁：稍等一下让重建方写完缓存，然后重试读缓存
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            html = redisService.get(GoodsKey.getGoodsList, "", String.class);
            if (!StringUtils.isEmpty(html)) {
                return html;
            }
            // 仍未命中说明重建方失败或尚未写完，本请求降级为自行渲染，保证有响应
        }
        try {
            // 3. 查询数据并手动渲染模板
            model.addAttribute("user", user);
            addListModelAttrs(model, goodsService.getGoodsVoList());
            WebContext context = new WebContext(request, response,
                    request.getServletContext(), request.getLocale(), model.asMap());
            html = templateEngine.process("goods_list", context);
            // 4. 将渲染好的 HTML 存入缓存，下次请求直接命中
            if (!StringUtils.isEmpty(html)) {
                redisService.set(GoodsKey.getGoodsList, "", html);
            }
            return html;
        } finally {
            // 5. 只释放自己抢到的锁，未抢到锁的降级请求不能误删他人的锁
            if (locked) {
                redisService.delete(GoodsKey.getGoodsListRebuildLock, "");
            }
        }
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
        if (goods.isInvalidWindow()) {
            return renderErrorPage(model, user, request, response, ResultCode.GOODS_TIME_INVALID);
        }
        model.addAttribute("goods", goods);
        // 状态由模板读取 goods.activityStatus，此处只需倒计时
        model.addAttribute("remainingSeconds", goods.getRemainingSeconds());

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
     * 列表页公共模型数据：商品列表 + Banner 轮播数据 + 分类集合。
     * Banner 规则：优先取进行中的活动（最多 3 条），无进行中活动时用列表前 3 条兜底。
     */
    private void addListModelAttrs(Model model, List<GoodsVo> goodsList) {
        model.addAttribute("goodsList", goodsList);
        List<GoodsVo> banners = new ArrayList<>();
        for (GoodsVo goods : goodsList) {
            if (goods.getActivityStatus() == 1 && banners.size() < 3) {
                banners.add(goods);
            }
        }
        if (banners.isEmpty() && !goodsList.isEmpty()) {
            banners = new ArrayList<>(goodsList.subList(0, Math.min(3, goodsList.size())));
        }
        model.addAttribute("bannerGoods", banners);
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        for (GoodsVo goods : goodsList) {
            String category = goods.getCategory();
            if (category != null && !category.trim().isEmpty()) {
                categories.add(category.trim());
            }
        }
        model.addAttribute("categories", categories);
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
     * <p>防<b>缓存穿透</b>：
     * <ul>
     *   <li>接口公开且无缓存，容易被脚本用不存在的 goodsId 刷库，故入口加
     *       {@link AccessLimit}（每 IP 每秒 10 次）限制刷量；</li>
     *   <li>DB 查不到的 ID 在 Redis 写入 60s 空值缓存（{@link GoodsKey#getGoodsNull}），
     *       窗口内的重复请求直接短路返回 GOODS_NOT_EXIST，不再落到 DB。</li>
     * </ul>
     *
     * @param goodsId 商品 ID（路径变量）
     * @return 包含 {@link GoodsDetailVo}（商品信息 + 秒杀状态 + 用户信息）的统一 JSON 响应
     */
    @AccessLimit(seconds = 1, maxCount = 10, needLogin = false)
    @RequestMapping(value = "/detailStatic/{goodsId}")
    @ResponseBody
    public Result<GoodsDetailVo> detailStaticPage(Model model, SeckillUser user,
                                                  HttpServletRequest request, HttpServletResponse response,
                                                  @PathVariable("goodsId") long goodsId) {
        logger.info("页面静态化/detail/{goodsId}");

        if (goodsId <= 0) {
            return Result.error(ResultCode.GOODS_NOT_EXIST);
        }
        // 1. 空值缓存命中：该 ID 在 60s 内已被确认不存在，直接短路，不再查库
        if (redisService.existsKey(GoodsKey.getGoodsNull, String.valueOf(goodsId))) {
            return Result.error(ResultCode.GOODS_NOT_EXIST);
        }
        // 2. 缓存未命中，查库
        GoodsVo goodsVo = goodsService.getGoodsVoByGoodsId(goodsId);
        if (goodsVo == null) {
            // 3. 回填空值缓存，防止同一不存在 ID 被反复刷库（缓存穿透）
            redisService.set(GoodsKey.getGoodsNull, String.valueOf(goodsId), "1");
            return Result.error(ResultCode.GOODS_NOT_EXIST);
        }
        if (goodsVo.isInvalidWindow()) {
            return Result.error(ResultCode.GOODS_TIME_INVALID);
        }
        // 组装 VO，将所有需要的数据一次性返回给前端
        GoodsDetailVo gdVo = new GoodsDetailVo();
        gdVo.setGoodsVo(goodsVo);
        gdVo.setStatus(goodsVo.getActivityStatus());
        gdVo.setRemainingSeconds(goodsVo.getRemainingSeconds());
        gdVo.setUser(user);
        return Result.success(gdVo);
    }
}

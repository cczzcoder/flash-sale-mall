package com.lijs.seckill.controller;

import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.service.GoodsService;
import com.lijs.seckill.service.SeckillUserService;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.vo.ChangePasswordVo;
import com.lijs.seckill.vo.ProfileVo;
import com.lijs.seckill.vo.RegisterVo;
import com.lijs.seckill.vo.UserInfoVo;
import com.lijs.seckill.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

/**
 * 用户控制器，提供当前登录用户的基础信息查询和商品详情跳转功能。
 *
 * <p>方法参数中的 {@code SeckillUser user} 由
 * {@link com.lijs.seckill.config.UserArgumentResolver} 自动从 token 中解析注入，
 * Controller 无需手动解析 Cookie。
 */
@RequestMapping("/user")
@Controller
public class UserController {

    @Autowired
    private GoodsService goodsService;
    @Autowired
    private SeckillUserService seckillUserService;

    @RequestMapping("/register")
    @ResponseBody
    public Result<Boolean> register(@Valid RegisterVo registerVo) {
        ResultCode result = seckillUserService.register(registerVo);
        return result.getCode() == 0 ? Result.success(true) : Result.error(result);
    }

    @RequestMapping("/password")
    @ResponseBody
    public Result<Boolean> changePassword(SeckillUser user, @Valid ChangePasswordVo changePasswordVo,
                                          HttpServletRequest request, HttpServletResponse response) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        ResultCode result = seckillUserService.changePassword(user.getId(), changePasswordVo);
        if (result.getCode() != 0) {
            return Result.error(result);
        }
        seckillUserService.logout(resolveToken(request), response);
        return Result.success(true);
    }

    @RequestMapping("/logout")
    @ResponseBody
    public Result<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        seckillUserService.logout(resolveToken(request), response);
        return Result.success(true);
    }

    /**
     * 更新个人资料（POST /user/profile）。
     * 只需昵称与头像；手机号即登录账号，不可修改。
     *
     * @param user      由 UserArgumentResolver 自动注入的当前用户
     * @param profileVo 昵称（非空）+ 头像 URL（可空）
     * @param request   用于提取 token，同步刷新 token → user 缓存
     */
    @RequestMapping("/profile")
    @ResponseBody
    public Result<Boolean> updateProfile(SeckillUser user, @Valid ProfileVo profileVo,
                                         HttpServletRequest request) {
        if (user == null) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        ResultCode result = seckillUserService.updateProfile(user.getId(), profileVo, resolveToken(request));
        return result.getCode() == 0 ? Result.success(true) : Result.error(result);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!org.apache.commons.lang3.StringUtils.isEmpty(header)) {
            return header;
        }
        String parameter = request.getParameter(SeckillUserService.COOKIE_NAME_TOKEN);
        if (!org.apache.commons.lang3.StringUtils.isEmpty(parameter)) {
            return parameter;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SeckillUserService.COOKIE_NAME_TOKEN.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 获取当前登录用户信息（GET /user/info）。
     * 未登录时 user 为 null，data 字段为 null。
     *
     * @param user 由 UserArgumentResolver 自动注入的当前用户
     * @return 包含 SeckillUser 对象的统一响应
     */
    @RequestMapping("/info")
    @ResponseBody
    public Result<UserInfoVo> info(SeckillUser user) {
        return Result.success(UserInfoVo.from(user));
    }

    /**
     * 跳转商品详情页（GET /user/to_detail/{goodsId}）。
     * 未使用页面缓存，直接渲染 Thymeleaf 模板；高并发时建议走
     * {@link GoodsController#goodsDetailCache} 的缓存版本。
     *
     * <p>传入模型数据：
     * <ul>
     *   <li>user      — 当前登录用户</li>
     *   <li>goods     — 商品+秒杀价格等信息（GoodsVo）</li>
     *   <li>remainingSeconds — 距开始的倒计时秒数（已结束时为 -1）</li>
     * </ul>
     *
     * @param goodsId 商品 ID（路径变量）
     * @return 视图名 "goods_detail"
     */
    @RequestMapping("/to_detail/{goodsId}")
    public String toDetail(Model model, SeckillUser user, @PathVariable("goodsId") long goodsId) {
        model.addAttribute("user", user);
        if (goodsId <= 0) {
            model.addAttribute("errorMessage", ResultCode.GOODS_NOT_EXIST.getMsg());
            return "seckill_fail";
        }
        GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
        if (goods == null) {
            model.addAttribute("errorMessage", ResultCode.GOODS_NOT_EXIST.getMsg());
            return "seckill_fail";
        }
        if (goods.isInvalidWindow()) {
            model.addAttribute("errorMessage", ResultCode.GOODS_TIME_INVALID.getMsg());
            return "seckill_fail";
        }
        model.addAttribute("goods", goods);
        model.addAttribute("remainingSeconds", goods.getRemainingSeconds());
        return "goods_detail";
    }
}

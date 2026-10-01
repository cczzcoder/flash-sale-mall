package com.lijs.seckill.controller;

import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.access.AccessLimit;
import com.lijs.seckill.service.SeckillUserService;
import com.lijs.seckill.vo.LoginVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

/**
 * 登录控制器，负责处理用户登录相关请求。
 *
 * <p>接口说明：
 * <ul>
 *   <li>GET  /login/index     → 跳转登录页面（Thymeleaf 模板 login.html）</li>
 *   <li>POST /login/do_login  → 正式登录，验证手机号 + 两次 MD5 密码，成功后写 Cookie</li>
 * </ul>
 *
 * <p>密码安全策略：前端先用固定 salt 做一次 MD5（inputPass → formPass），
 * 再由服务端用数据库随机 salt 做第二次 MD5（formPass → dbPass），
 * 两次加密保证密码既不在网络明文传输，数据库中也不存原始密码。
 */
@RequestMapping("/login")
@Controller
public class LoginController {

    @Autowired
    private SeckillUserService seckillUserService;

    private final Logger logger = (Logger) LoggerFactory.getLogger(Logger.class);

    /**
     * 跳转登录首页（GET /login/index）。
     * 返回视图名 "login"，对应 resources/templates/login.html。
     */
    @RequestMapping("/index")
    public String toLogin() {
        logger.info("跳转登录首页...");
        return "login";
    }

    /**
     * 正式登录接口（POST /login/do_login）。
     * 登录成功后将 token 写入 Cookie，前端后续请求携带该 Cookie 完成身份验证。
     *
     * @param response HTTP 响应，用于写 Set-Cookie 头
     * @param loginVo  登录参数，@Valid 触发参数校验（手机号格式、密码非空等）
     * @return code=0 表示登录成功；非 0 返回对应的错误码和错误信息
     */
    @RequestMapping("/do_login")
    @ResponseBody
    @AccessLimit(seconds = 60, maxCount = 5, needLogin = false)
    public Result<Boolean> doLogin(HttpServletResponse response, @Valid LoginVo loginVo) {
        ResultCode resultCode = seckillUserService.login(response, loginVo);
        if (resultCode.getCode() == 0) {
            return Result.success(true);
        } else {
            return Result.error(resultCode);
        }
    }
}

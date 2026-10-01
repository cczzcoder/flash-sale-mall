package com.lijs.seckill.access;

import com.alibaba.fastjson.JSON;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.AccessKey;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.SeckillUserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 全局请求拦截器，承担两项职责：
 * <ol>
 *   <li><b>分布式 Session 解析</b>：从 Cookie / Authorization 请求头中提取 token，
 *       通过 Redis 还原当前用户，并存入 {@link UserContext}，供后续 Controller 直接使用。</li>
 *   <li><b>接口限流</b>：识别方法上的 {@link AccessLimit} 注解，在 Redis 中对（URI + 用户ID）
 *       计数，超过窗口内最大次数则直接以 JSON 响应 ACCESS_LIMIT，不放行请求。</li>
 * </ol>
 *
 * <p>注册入口：{@link com.lijs.seckill.config.WebConfig#addInterceptors}
 */
@Service
public class AccessInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    private SeckillUserService seckillUserService;
    @Autowired
    private RedisService redisService;

    /**
     * 请求到达 Controller 之前执行。
     * 返回 true 表示放行，返回 false 表示拦截（已向 response 写入错误信息）。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (handler instanceof HandlerMethod) {
            // ① 解析 token → 获取用户，存入 ThreadLocal 供本次请求全程使用
            SeckillUser user = getUser(request, response);
            UserContext.setUser(user);

            HandlerMethod hm = (HandlerMethod) handler;
            // ② 读取目标方法上的 @AccessLimit 注解；没有注解则直接放行
            AccessLimit accessLimit = hm.getMethodAnnotation(AccessLimit.class);
            if (accessLimit == null) {
                return true;
            }

            // ③ 取出注解参数
            int seconds  = accessLimit.seconds();   // 计数窗口（秒）
            int maxCount = accessLimit.maxCount();   // 窗口内最大请求次数
            boolean needLogin = accessLimit.needLogin();

            // 限流 key = URI；若需登录则追加用户 ID，使每个用户独立计数
            String key = request.getRequestURI();
            if (needLogin) {
                if (user == null) {
                    // 需要登录但 token 无效 / 缺失
                    render(response, ResultCode.SESSION_ERROR);
                    return false;
                }
                key += "_" + user.getId();
            } else {
                // 未登录接口按来源和账号维度计数，避免同一 NAT 下所有用户共享一个桶。
                key += "_" + request.getRemoteAddr();
                String account = request.getParameter("mobile");
                if (StringUtils.isEmpty(account)) {
                    account = request.getParameter("username");
                }
                if (!StringUtils.isEmpty(account)) {
                    key += "_" + account;
                }
            }

            // ④ Redis 计数：动态设置 TTL（seconds），窗口到期后自动重置
            AccessKey accessKey = AccessKey.expire(seconds);
            Integer count = redisService.get(accessKey, key, Integer.class);
            if (count == null) {
                // 第一次访问，初始化计数为 1
                redisService.set(accessKey, key, 1);
            } else if (count < maxCount) {
                // 未超限，计数 +1
                redisService.incr(accessKey, key);
            } else {
                // 已超限，返回限流错误给前端
                render(response, ResultCode.ACCESS_LIMIT);
                return false;
            }
        }
        return super.preHandle(request, response, handler);
    }

    /**
     * 将错误码序列化为 JSON 并直接写入响应体（绕过 MVC 视图解析）。
     * 用于拦截器中快速返回错误，不需要经过 Controller。
     */
    private void render(HttpServletResponse response, ResultCode cm) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        OutputStream out = response.getOutputStream();
        String jsonString = JSON.toJSONString(Result.error(cm));
        out.write(jsonString.getBytes("UTF-8"));
        out.flush();
        out.close();
    }

    /**
     * 从请求中提取 token 并查询对应用户。
     *
     * <p>token 来源优先级（从高到低）：
     * <ol>
     *   <li>Authorization 请求头（适合 App / 接口调用场景）</li>
     *   <li>Cookie（适合传统浏览器场景）</li>
     * </ol>
     * 以上均为空则返回 null（未登录）。
     */
    private SeckillUser getUser(HttpServletRequest request, HttpServletResponse response) {
        String headerToken = request.getHeader("Authorization");
        String cookieToken = getCookieValue(request, SeckillUserService.COOKIE_NAME_TOKEN);
        if (StringUtils.isEmpty(cookieToken) && StringUtils.isEmpty(headerToken)) {
            return null;
        }
        // API clients may use Authorization; browsers must use the HttpOnly cookie.
        String token = StringUtils.isEmpty(headerToken) ? cookieToken : headerToken;
        // 通过 Redis 中的 token → 用户映射还原用户，同时顺延 cookie 有效期
        return seckillUserService.getByToken(token, response);
    }

    /**
     * 从请求的所有 Cookie 中查找指定名称的 Cookie 值。
     *
     * @param cookieNameToken 要查找的 Cookie 名称
     * @return Cookie 值；不存在则返回 null
     */
    public String getCookieValue(HttpServletRequest request, String cookieNameToken) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieNameToken)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}

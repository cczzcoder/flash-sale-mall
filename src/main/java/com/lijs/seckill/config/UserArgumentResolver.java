package com.lijs.seckill.config;

import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.service.SeckillUserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 自定义方法参数解析器，让 Controller 方法中的 {@code SeckillUser} 参数
 * 自动从请求的 token 中解析并注入，无需在每个方法里手动解析 Cookie。
 *
 * <p>工作原理：
 * <ol>
 *   <li>Spring MVC 调用 {@link #supportsParameter}，判断当前参数是否为 {@code SeckillUser} 类型。</li>
 *   <li>若是，调用 {@link #resolveArgument}，从请求中提取 token，通过 Redis 还原用户对象并返回。</li>
 *   <li>返回值被注入到 Controller 方法对应的参数位置。</li>
 * </ol>
 *
 * <p>注册入口：{@link WebConfig#addArgumentResolvers}
 */
@Service
public class UserArgumentResolver implements HandlerMethodArgumentResolver {

    @Autowired
    private SeckillUserService seckillUserService;

    /**
     * 从请求中提取 token 并解析为 {@link SeckillUser}。
     *
     * <p>token 来源优先级（从高到低）：
     * <ol>
     *   <li>Authorization 请求头（App / 接口调用场景）</li>
     *   <li>Cookie（传统浏览器场景）</li>
     * </ol>
     * 两处均为空时返回 null（未登录）。
     *
     * @return SeckillUser 对象；未登录或 token 失效时返回 null
     */
    @Override
    public Object resolveArgument(MethodParameter arg0, ModelAndViewContainer arg1,
                                  NativeWebRequest webRequest, WebDataBinderFactory arg3) {
        HttpServletRequest  request  = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

        String headerToken = request.getHeader("Authorization");
        String cookieToken = getCookieValue(request, SeckillUserService.COOKIE_NAME_TOKEN);

        if (StringUtils.isEmpty(cookieToken) && StringUtils.isEmpty(headerToken)) {
            return null;
        }
        String token = StringUtils.isEmpty(headerToken) ? cookieToken : headerToken;
        SeckillUser user = seckillUserService.getByToken(token, response);
        return user;
    }

    /**
     * 从请求的 Cookie 列表中按名称查找对应 Cookie 的值。
     *
     * @param cookieNameToken 要查找的 Cookie 名称（通常为 "token"）
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

    /**
     * 判断当前参数是否由本解析器处理。
     * 只处理参数类型为 {@link SeckillUser} 的方法参数。
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> clazz = parameter.getParameterType();
        return clazz == SeckillUser.class;
    }
}

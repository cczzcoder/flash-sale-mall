package com.lijs.seckill.config;

import com.lijs.seckill.access.AccessInterceptor;
import com.lijs.seckill.access.CsrfInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import java.util.List;

/**
 * Spring MVC 全局配置。
 *
 * <p>主要职责：
 * <ul>
 *   <li>注册自定义参数解析器 {@link UserArgumentResolver}，使 Controller 方法可以
 *       直接声明 {@code SeckillUser user} 参数，框架自动从 token 中解析并注入。</li>
 *   <li>注册全局拦截器 {@link AccessInterceptor}，覆盖所有路径，
 *       负责 token 解析和 @AccessLimit 限流。</li>
 * </ul>
 */
@Configuration
public class WebConfig extends WebMvcConfigurerAdapter {

    private final UserArgumentResolver userArgumentResolver;
    private final AccessInterceptor accessInterceptor;
    private final CsrfInterceptor csrfInterceptor;

    /** 构造器注入：Spring 保证参数非 null，IDE 不再产生 null-safety 警告 */
    public WebConfig(UserArgumentResolver userArgumentResolver,
                     AccessInterceptor accessInterceptor,
                     CsrfInterceptor csrfInterceptor) {
        this.userArgumentResolver = userArgumentResolver;
        this.accessInterceptor    = accessInterceptor;
        this.csrfInterceptor      = csrfInterceptor;
    }

    /**
     * 注册自定义方法参数解析器。
     * Spring MVC 在解析 Controller 方法参数时，会遍历所有已注册的解析器，
     * 调用 {@link UserArgumentResolver#supportsParameter} 判断是否支持，
     * 支持则调用 {@link UserArgumentResolver#resolveArgument} 完成注入。
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(userArgumentResolver);
    }

    /**
     * 注册全局拦截器。
     * AccessInterceptor 承担两项职责：
     *   1. 解析请求中的 token，将用户写入 ThreadLocal（UserContext），供后续 Controller 使用。
     *   2. 识别方法上的 @AccessLimit 注解，对（URI + 用户）组合进行 Redis 计数限流。
     * 拦截所有路径（"/**"），但仅当方法有 @AccessLimit 注解时才触发限流逻辑，
     * 无注解的接口只做 token 解析，不影响正常访问。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessInterceptor).addPathPatterns("/**");
        registry.addInterceptor(csrfInterceptor).addPathPatterns("/**");
    }
}

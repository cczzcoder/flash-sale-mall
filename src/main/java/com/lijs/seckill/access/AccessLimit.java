package com.lijs.seckill.access;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 接口访问频率限制注解。
 *
 * <p>标注在 Controller 方法上，由 {@link AccessInterceptor} 拦截器统一处理。
 * 限流策略：在 {@code seconds} 秒的滑动窗口内，同一（URI + 用户）组合最多允许访问 {@code maxCount} 次，
 * 超出后拦截并返回 {@link com.lijs.seckill.result.ResultCode#ACCESS_LIMIT}。
 *
 * <p>使用示例：
 * <pre>{@code
 * @AccessLimit(seconds = 5, maxCount = 5, needLogin = true)
 * @RequestMapping("/seckill/getPath")
 * public Result<String> getSeckillPath(...) { ... }
 * }</pre>
 */
@Target(METHOD)      // 只能标注在方法上
@Retention(RUNTIME)  // 运行时保留，拦截器通过反射读取
public @interface AccessLimit {

    /** 时间窗口大小（秒），即计数器在 Redis 中的 TTL */
    int seconds();

    /** 窗口内最大允许访问次数，超过则触发限流 */
    int maxCount();

    /**
     * 是否需要登录。
     * true（默认）：限流 key = URI + 用户ID，未登录时直接返回 SESSION_ERROR。
     * false：限流 key = URI（全局限流），不校验登录态。
     */
    boolean needLogin() default true;
}

package com.lijs.seckill.access;

import com.lijs.seckill.domain.SeckillUser;

/**
 * 基于 ThreadLocal 的用户上下文，用于在同一请求线程内共享当前登录用户信息。
 *
 * <p>工作流程：
 * <ol>
 *   <li>{@link AccessInterceptor#preHandle} 在请求进入 Controller 前，
 *       解析 token 并调用 {@link #setUser(SeckillUser)} 将用户存入 ThreadLocal。</li>
 *   <li>Controller 方法可通过 {@link #getUser()} 随时取出当前用户，
 *       也可以直接在方法参数上声明 {@code SeckillUser user}，
 *       由 {@link com.lijs.seckill.config.UserArgumentResolver} 自动注入。</li>
 * </ol>
 *
 * <p>ThreadLocal 的值与当前线程绑定，不同请求之间相互隔离，天然线程安全。
 * 注意：Spring 默认使用线程池复用线程，如需避免内存泄漏，
 * 应在请求结束后（afterCompletion）调用 {@code userHolder.remove()}。
 */
public class UserContext {

    /** 每个线程独立持有一份 SeckillUser 引用 */
    private static final ThreadLocal<SeckillUser> userHolder = new ThreadLocal<SeckillUser>();

    /** 将当前用户存入本线程的 ThreadLocal，在拦截器 preHandle 中调用 */
    public static void setUser(SeckillUser user) {
        userHolder.set(user);
    }

    /** 获取当前线程绑定的用户，拦截器未设置或用户未登录时返回 null */
    public static SeckillUser getUser() {
        return userHolder.get();
    }
}

package com.lijs.seckill.util;

import java.util.UUID;

/**
 * UUID 工具类，生成无连字符的随机唯一字符串。
 *
 * <p>主要用于生成分布式 Session 的 token：
 * 标准 UUID 格式为 {@code xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx}（含 4 个"-"共 36 位），
 * 去掉连字符后得到 32 位纯十六进制字符串，更适合作为 Cookie / Redis key 的值。
 *
 * <p>使用场景：
 * <ul>
 *   <li>{@link com.lijs.seckill.service.SeckillUserService#login} — 登录成功后生成 token</li>
 *   <li>{@link com.lijs.seckill.service.SeckillService#createSeckillPath} — 生成一次性秒杀 Path</li>
 * </ul>
 */
public class UUIDUtil {

    /**
     * 生成 32 位无连字符的随机 UUID 字符串。
     * 例：{@code a1b2c3d4e5f6...}（标准 UUID 去掉"-"）
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

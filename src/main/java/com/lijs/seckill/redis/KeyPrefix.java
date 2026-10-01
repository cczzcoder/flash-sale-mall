package com.lijs.seckill.redis;

/**
 * Redis Key 前缀接口，统一管理所有 Redis key 的命名规范和过期时间。
 *
 * <p>设计思路：将 Redis key 的"业务前缀 + 过期时间"封装成对象，
 * 避免硬编码字符串散落在各处，同时防止不同业务的 key 命名冲突。
 *
 * <p>key 最终格式：{@code 类名:prefix + 业务key}，例如：
 * {@code GoodsKey:gl1}（商品列表页缓存，商品 ID 为 1）
 *
 * <p>实现类：{@link BasePrefix}（提供默认实现）
 * 具体实例：{@link GoodsKey}、{@link SeckillKey}、{@link OrderKey} 等
 */
public interface KeyPrefix {

    /**
     * 缓存过期时间（秒）。
     * 返回 0 或负数表示永不过期。
     */
    int expireSeconds();

    /**
     * 生成完整的 Redis key 前缀（通常为 "类名:业务标识"）。
     * 最终的完整 key = getPrefix() + 业务key。
     */
    String getPrefix();
}

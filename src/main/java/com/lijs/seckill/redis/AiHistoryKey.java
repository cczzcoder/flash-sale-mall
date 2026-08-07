package com.lijs.seckill.redis;

/**
 * AI 导购对话历史的 Redis Key 前缀。
 * TTL = 24 小时，超时自动清理，多实例共享同一份对话历史。
 */
public class AiHistoryKey extends BasePrefix {

    /** 对话历史 List，TTL 24h */
    public static final AiHistoryKey history = new AiHistoryKey(24 * 3600, "h");

    private AiHistoryKey(int expireSeconds, String prefix) {
        super(expireSeconds, prefix);
    }
}

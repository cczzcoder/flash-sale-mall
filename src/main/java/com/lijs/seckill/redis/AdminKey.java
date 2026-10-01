package com.lijs.seckill.redis;

public class AdminKey extends BasePrefix {
    private AdminKey(int expireSeconds, String prefix) { super(expireSeconds, prefix); }

    public static final AdminKey token = new AdminKey(7200, "tk");
}

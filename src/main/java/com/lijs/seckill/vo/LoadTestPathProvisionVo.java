package com.lijs.seckill.vo;

/** One user-scoped path emitted by the local load-test-only provisioner. */
public class LoadTestPathProvisionVo {

    private final Long userId;
    private final Long goodsId;
    private final String path;

    public LoadTestPathProvisionVo(Long userId, Long goodsId, String path) {
        this.userId = userId;
        this.goodsId = goodsId;
        this.path = path;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public String getPath() {
        return path;
    }
}

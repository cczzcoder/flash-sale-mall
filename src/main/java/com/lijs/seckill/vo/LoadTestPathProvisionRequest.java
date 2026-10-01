package com.lijs.seckill.vo;

import java.util.List;

/** Request body for the local load-test-only path provisioner. */
public class LoadTestPathProvisionRequest {

    private Long goodsId;
    private List<Long> userIds;

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public List<Long> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<Long> userIds) {
        this.userIds = userIds;
    }
}

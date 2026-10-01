package com.lijs.seckill.vo;

import java.util.Date;

/** 秒杀活动窗口缓存对象，不包含库存等高频变化字段。 */
public class SeckillWindowVo {
    private Date startDate;
    private Date endDate;

    public SeckillWindowVo() {
    }

    public SeckillWindowVo(Date startDate, Date endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
}

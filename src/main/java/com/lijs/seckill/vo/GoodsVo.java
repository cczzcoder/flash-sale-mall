package com.lijs.seckill.vo;

import com.lijs.seckill.domain.Goods;

import java.util.Date;

/**
 * 商品视图对象，继承 {@link Goods}（商品基础信息），并追加秒杀相关字段。
 *
 * <p>对应 Mapper 中的联合查询（goods LEFT JOIN seckill_goods），
 * 将 goods 表和 seckill_goods 表的字段合并到一个对象，
 * 方便前端一次获取商品展示和秒杀活动所需的全部数据。
 *
 * <p>字段说明：
 * <ul>
 *   <li>stockCount    — 当前秒杀剩余库存（来自 seckill_goods.stock_count）</li>
 *   <li>startDate     — 秒杀开始时间</li>
 *   <li>endDate       — 秒杀结束时间</li>
 *   <li>seckillPrice  — 秒杀价格（通常远低于商品原价 goods.goodsPrice）</li>
 *   <li>version       — 乐观锁版本号（预留，当前版本使用 stock_count > 0 条件防超卖）</li>
 * </ul>
 */
public class GoodsVo extends Goods {

    /** 秒杀剩余库存数量 */
    private Integer stockCount;
    /** 秒杀活动开始时间 */
    private Date startDate;
    /** 秒杀活动结束时间 */
    private Date endDate;
    /** 乐观锁版本号（预留字段） */
    private Integer version;
    /** 秒杀价格 */
    private Double seckillPrice;
    /** 所属店铺名称，NULL/空 表示平台自营（由列表查询 join 填充） */
    private String shopName;

    public Double  getSeckillPrice()              { return seckillPrice; }
    public void    setSeckillPrice(Double v)      { this.seckillPrice = v; }
    public String  getShopName()                  { return shopName; }
    public void    setShopName(String v)          { this.shopName = v; }
    public Integer getStockCount()                { return stockCount; }
    public void    setStockCount(Integer v)       { this.stockCount = v; }
    public Date    getStartDate()                 { return startDate; }
    public void    setStartDate(Date v)           { this.startDate = v; }
    public Date    getEndDate()                   { return endDate; }
    public void    setEndDate(Date v)             { this.endDate = v; }
    public Integer getVersion()                   { return version; }
    public void    setVersion(Integer v)          { this.version = v; }

    /** 秒杀时间窗是否非法：缺少时间或结束时间不晚于开始时间。 */
    public boolean isInvalidWindow() {
        return startDate == null || endDate == null || !startDate.before(endDate);
    }

    /** 0=未开始，1=进行中，2=已结束。用于列表和详情页展示。 */
    public int getActivityStatus() {
        if (isInvalidWindow()) {
            return 2;
        }
        Date now = new Date();
        if (now.before(startDate)) return 0;
        if (now.after(endDate)) return 2;
        return 1;
    }

    /** 距秒杀开始的倒计时秒数：未开始>0，进行中=0，已结束=-1。用于详情页倒计时。 */
    public int getRemainingSeconds() {
        if (startDate == null || endDate == null) {
            return -1;
        }
        long now = System.currentTimeMillis();
        long start = startDate.getTime();
        if (now < start) {
            return (int) ((start - now) / 1000);
        }
        return now > endDate.getTime() ? -1 : 0;
    }

    public boolean isSoldOut() {
        return stockCount == null || stockCount <= 0;
    }
}

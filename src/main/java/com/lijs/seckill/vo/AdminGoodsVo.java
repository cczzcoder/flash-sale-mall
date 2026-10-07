package com.lijs.seckill.vo;

import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

public class AdminGoodsVo {
    private Long goodsId;
    @NotBlank private String goodsName;
    @NotBlank private String goodsTitle;
    private String goodsImg;
    private String goodsDetail;
    private String category;
    @NotNull @DecimalMin("0.00") private Double goodsPrice;
    @NotNull @DecimalMin("0.00") private Double seckillPrice;
    @NotNull @Min(0) private Integer stockCount;
    @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date startDate;
    @NotNull @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") private Date endDate;

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }
    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }
    public String getGoodsTitle() { return goodsTitle; }
    public void setGoodsTitle(String goodsTitle) { this.goodsTitle = goodsTitle; }
    public String getGoodsImg() { return goodsImg; }
    public void setGoodsImg(String goodsImg) { this.goodsImg = goodsImg; }
    public String getGoodsDetail() { return goodsDetail; }
    public void setGoodsDetail(String goodsDetail) { this.goodsDetail = goodsDetail; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Double getGoodsPrice() { return goodsPrice; }
    public void setGoodsPrice(Double goodsPrice) { this.goodsPrice = goodsPrice; }
    public Double getSeckillPrice() { return seckillPrice; }
    public void setSeckillPrice(Double seckillPrice) { this.seckillPrice = seckillPrice; }
    public Integer getStockCount() { return stockCount; }
    public void setStockCount(Integer stockCount) { this.stockCount = stockCount; }
    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }
    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }
}

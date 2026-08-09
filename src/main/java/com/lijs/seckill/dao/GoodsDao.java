package com.lijs.seckill.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijs.seckill.domain.SeckillGoods;
import com.lijs.seckill.vo.GoodsVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GoodsDao extends BaseMapper<SeckillGoods> {

    // 多表 JOIN 查询，无法由 BaseMapper 替代，保留注解 SQL
    @Select("select g.*,mg.stock_count,mg.start_date,mg.end_date,mg.seckill_price" +
            " from seckill_goods mg left join goods g on mg.goods_id=g.id")
    List<GoodsVo> getGoodsVoList();

    @Select("select g.*,mg.stock_count,mg.start_date,mg.end_date,mg.seckill_price" +
            " from seckill_goods mg left join goods g on mg.goods_id=g.id where g.id=#{goodsId}")
    GoodsVo getGoodsVoByGoodsId(@Param("goodsId") long goodsId);

    /**
     * 原子性减库存：stock_count > 0 时才执行，利用数据库行锁防止超卖。
     * TODO 可升级为乐观锁版本（version 字段）
     */
    @Update("update seckill_goods set stock_count = stock_count - 1" +
            " where goods_id = #{goodsId} and stock_count > 0")
    int reduceStock(SeckillGoods goods);

    @Update("update seckill_goods set stock_count = stock_count - 1" +
            " where goods_id = #{goodsId} and stock_count > 0")
    int reduceStockLock(SeckillGoods goods);
}

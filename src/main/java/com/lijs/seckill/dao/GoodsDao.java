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
    @Select("select g.*,mg.stock_count,mg.stock_total,mg.start_date,mg.end_date,mg.seckill_price" +
            " from seckill_goods mg left join goods g on mg.goods_id=g.id")
    List<GoodsVo> getGoodsVoList();

    @Select("select g.*,mg.stock_count,mg.stock_total,mg.start_date,mg.end_date,mg.seckill_price" +
            " from seckill_goods mg left join goods g on mg.goods_id=g.id where g.id=#{goodsId}")
    GoodsVo getGoodsVoByGoodsId(@Param("goodsId") long goodsId);

    /**
     * 原子性扣减库存：stock_count > 0 时才执行，靠 InnoDB 行锁防止超卖。
     * 返回受影响行数，0 表示库存已耗尽，调用方据此判定失败，无需重试。
     * 依赖 seckill_goods 上的 uk_goods_id 唯一索引，否则退化为全表扫描并锁全表。
     */
    @Update("update seckill_goods set stock_count = stock_count - 1" +
            " where goods_id = #{goodsId} and stock_count > 0")
    int reduceStock(SeckillGoods goods);

    /** 订单关闭时在同一数据库事务中按订单数量恢复库存。 */
    @Update("update seckill_goods set stock_count = stock_count + #{count} where goods_id = #{goodsId}")
    int increaseStock(@Param("goodsId") long goodsId, @Param("count") int count);

    /**
     * 对账用：读取所有秒杀商品的 goods_id 与当前 DB 库存。
     * 只取对账需要的两列，避免 JOIN goods 表。
     */
    @Select("select goods_id, stock_count from seckill_goods")
    List<SeckillGoods> listStockForReconcile();
}

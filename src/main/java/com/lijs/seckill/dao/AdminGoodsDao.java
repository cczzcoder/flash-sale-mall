package com.lijs.seckill.dao;

import com.lijs.seckill.domain.Goods;
import com.lijs.seckill.domain.SeckillGoods;
import com.lijs.seckill.vo.GoodsVo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminGoodsDao {
    @Select("select g.*, s.stock_count, s.start_date, s.end_date, s.seckill_price, sh.name as shopName "
            + "from goods g join seckill_goods s on s.goods_id=g.id "
            + "left join shop sh on sh.id=g.shop_id order by g.id desc")
    List<GoodsVo> list();

    @Select("select g.*, s.stock_count, s.start_date, s.end_date, s.seckill_price, sh.name as shopName "
            + "from goods g join seckill_goods s on s.goods_id=g.id "
            + "left join shop sh on sh.id=g.shop_id where g.shop_id=#{shopId} order by g.id desc")
    List<GoodsVo> listByShopId(@Param("shopId") long shopId);

    @Select("select g.*, s.stock_count, s.start_date, s.end_date, s.seckill_price, sh.name as shopName "
            + "from goods g join seckill_goods s on s.goods_id=g.id "
            + "left join shop sh on sh.id=g.shop_id where g.id=#{goodsId}")
    GoodsVo get(@Param("goodsId") long goodsId);

    @Select("select * from seckill_goods where goods_id=#{goodsId}")
    SeckillGoods getSeckill(@Param("goodsId") long goodsId);

    @Insert("insert into goods (goods_name,goods_title,goods_img,goods_detail,goods_price,goods_stock,shop_id) "
            + "values (#{goodsName},#{goodsTitle},#{goodsImg},#{goodsDetail},#{goodsPrice},#{goodsStock},#{shopId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertGoods(Goods goods);

    @Insert("insert into seckill_goods (goods_id,seckill_price,stock_count,version,start_date,end_date) "
            + "values (#{goodsId},#{seckillPrice},#{stockCount},0,#{startDate},#{endDate})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSeckill(SeckillGoods goods);

    @Update("update goods set goods_name=#{goodsName},goods_title=#{goodsTitle},goods_img=#{goodsImg},"
            + "goods_detail=#{goodsDetail},goods_price=#{goodsPrice} where id=#{id}")
    int updateGoods(Goods goods);

    @Update("update seckill_goods set seckill_price=#{seckillPrice},"
            + "start_date=#{startDate},end_date=#{endDate},version=version+1 where goods_id=#{goodsId}")
    int updateSeckill(SeckillGoods goods);

    @Update("update seckill_goods set stock_count=stock_count + #{count},version=version+1 "
            + "where goods_id=#{goodsId} and stock_count + #{count} <= 2147483647")
    int increaseSeckillStock(@Param("goodsId") long goodsId, @Param("count") int count);

    @Update("update goods set goods_stock=goods_stock + #{count} where id=#{goodsId}")
    int increaseGoodsStock(@Param("goodsId") long goodsId, @Param("count") int count);

    @Select("select count(1) from order_info where goods_id=#{goodsId}")
    int countOrders(@Param("goodsId") long goodsId);

    @Delete("delete from seckill_goods where goods_id=#{goodsId}")
    int deleteSeckill(@Param("goodsId") long goodsId);

    @Delete("delete from goods where id=#{goodsId}")
    int deleteGoods(@Param("goodsId") long goodsId);
}

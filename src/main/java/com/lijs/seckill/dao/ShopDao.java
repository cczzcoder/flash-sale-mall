package com.lijs.seckill.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijs.seckill.domain.Shop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShopDao extends BaseMapper<Shop> {

    @Select("select * from shop where owner_user_id=#{userId}")
    Shop selectByOwnerUserId(@Param("userId") long userId);

    /** 待审核的排在最前，方便管理员优先处理 */
    @Select("select * from shop order by status asc, id desc")
    List<Shop> listAll();

    @Update("update shop set status=#{status} where id=#{shopId}")
    int updateStatus(@Param("shopId") long shopId, @Param("status") int status);
}

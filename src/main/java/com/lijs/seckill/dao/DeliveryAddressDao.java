package com.lijs.seckill.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijs.seckill.domain.DeliveryAddress;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DeliveryAddressDao extends BaseMapper<DeliveryAddress> {

    @Select("select * from delivery_address where user_id=#{userId} order by default_address desc, update_date desc, id desc")
    List<DeliveryAddress> selectByUserId(@Param("userId") long userId);

    @Select("select * from delivery_address where id=#{addressId} and user_id=#{userId}")
    DeliveryAddress selectOwned(@Param("addressId") long addressId, @Param("userId") long userId);

    @Select("select * from delivery_address where user_id=#{userId} and default_address=1 order by id desc limit 1")
    DeliveryAddress selectDefault(@Param("userId") long userId);

    @Update("update delivery_address set default_address=0, update_date=now() where user_id=#{userId}")
    int clearDefault(@Param("userId") long userId);

    @Delete("delete from delivery_address where id=#{addressId} and user_id=#{userId}")
    int deleteOwned(@Param("addressId") long addressId, @Param("userId") long userId);
}

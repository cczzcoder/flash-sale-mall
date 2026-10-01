package com.lijs.seckill.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijs.seckill.domain.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminUserDao extends BaseMapper<AdminUser> {
    @Select("select * from admin_user where username=#{username} and enabled=1")
    AdminUser selectEnabledByUsername(@Param("username") String username);

    @Select("select * from seckill_user order by register_date desc, id desc limit #{limit}")
    List<com.lijs.seckill.domain.SeckillUser> selectRecentUsers(@Param("limit") int limit);
}

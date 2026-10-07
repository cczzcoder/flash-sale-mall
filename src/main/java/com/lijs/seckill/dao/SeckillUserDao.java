package com.lijs.seckill.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijs.seckill.domain.SeckillUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillUserDao extends BaseMapper<SeckillUser> {

    // 保留具名方法供 SeckillUserService 调用
    @Select("select * from seckill_user where id=#{id}")
    SeckillUser getById(@Param("id") long id);

    // 只更新 pwd 字段；如需全字段更新可改用 BaseMapper.updateById()
    @Update("update seckill_user set pwd=#{pwd}, salt=#{salt} where id=#{id}")
    int updatePassword(SeckillUser updateUser);

    @Update("update seckill_user set role=#{role} where id=#{userId}")
    int updateRole(@Param("userId") long userId, @Param("role") int role);
}

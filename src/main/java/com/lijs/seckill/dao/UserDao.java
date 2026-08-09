package com.lijs.seckill.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lijs.seckill.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserDao extends BaseMapper<User> {

    // 保留具名方法供 UserService 直接调用，底层仍走注解 SQL
    @Select("select * from t_user where id=#{id}")
    User getById(@Param("id") int id);

    // insert(User) 由 BaseMapper 提供（int insert(T entity)），此处不再重复声明，
    // UserService.tx() 中 userDao.insert(user) 可直接调用 BaseMapper 的 insert
}

package com.lijs.seckill.service;

import com.lijs.seckill.dao.AdminUserDao;
import com.lijs.seckill.domain.AdminUser;
import com.lijs.seckill.redis.AdminKey;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.util.MD5Util;
import com.lijs.seckill.util.UUIDUtil;
import com.lijs.seckill.vo.AdminLoginVo;
import com.lijs.seckill.vo.UserInfoVo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminAuthService {
    private final AdminUserDao adminUserDao;
    private final RedisService redisService;

    public AdminAuthService(AdminUserDao adminUserDao, RedisService redisService) {
        this.adminUserDao = adminUserDao;
        this.redisService = redisService;
    }

    public String login(AdminLoginVo loginVo) {
        AdminUser admin = adminUserDao.selectEnabledByUsername(loginVo.getUsername());
        if (admin == null || !MD5Util.formPassToDBPass(loginVo.getPassword(), admin.getSalt())
                .equals(admin.getPassword())) {
            return null;
        }
        String token = UUIDUtil.uuid();
        redisService.set(AdminKey.token, token, admin.getId());
        return token;
    }

    public boolean isValid(String token) {
        return getId(token) != null;
    }

    public Long getId(String token) {
        return token == null || token.trim().isEmpty()
                ? null : redisService.get(AdminKey.token, token, Long.class);
    }

    public void logout(String token) {
        if (token != null && !token.trim().isEmpty()) {
            redisService.delete(AdminKey.token, token);
        }
    }

    public List<UserInfoVo> listUsers(int limit) {
        return adminUserDao.selectRecentUsers(limit).stream()
                .map(UserInfoVo::from).collect(Collectors.toList());
    }
}

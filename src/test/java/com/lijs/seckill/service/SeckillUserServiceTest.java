package com.lijs.seckill.service;

import com.lijs.seckill.dao.SeckillUserDao;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.redis.SeckillUserKey;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.util.MD5Util;
import com.lijs.seckill.vo.LoginVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillUserServiceTest {

    private static final long MOBILE = 13800000000L;
    private static final String SALT = "abcd1234";

    @Mock private SeckillUserDao seckillUserDao;
    @Mock private RedisService redisService;
    @Mock private HttpServletResponse response;

    private SeckillUserService service;

    @BeforeEach
    void setUp() {
        service = new SeckillUserService();
        ReflectionTestUtils.setField(service, "seckillUserDao", seckillUserDao);
        ReflectionTestUtils.setField(service, "redisService", redisService);
    }

    @Test
    void loginReturnsServerErrorForNullVo() {
        assertEquals(ResultCode.SERVER_ERROR.getCode(), service.login(response, null).getCode());
        assertEquals(ResultCode.SERVER_ERROR.getMsg(), service.loginTest(response, null));
    }

    @Test
    void loginReturnsMobileNotExistForUnknownMobile() {
        when(seckillUserDao.getById(MOBILE)).thenReturn(null);

        assertEquals(ResultCode.MOBILE_NOT_EXIST.getCode(),
                service.login(response, loginVo("whatever")).getCode());
        assertEquals(ResultCode.MOBILE_NOT_EXIST.getMsg(), service.loginTest(response, loginVo("whatever")));
    }

    @Test
    void loginReturnsPasswordErrorForWrongPassword() {
        when(seckillUserDao.getById(MOBILE)).thenReturn(userWithPassword("rightPass"));

        assertEquals(ResultCode.PASSWORD_ERROR.getCode(),
                service.login(response, loginVo("wrongPass")).getCode());
    }

    @Test
    void loginWritesCookieAndRedisTokenOnSuccess() {
        SeckillUser user = userWithPassword("frontPass");
        when(seckillUserDao.getById(MOBILE)).thenReturn(user);

        assertEquals(ResultCode.SUCCESS.getCode(), service.login(response, loginVo("frontPass")).getCode());

        verify(response).addCookie(any(Cookie.class));
        verify(redisService).set(eq(SeckillUserKey.token), anyString(), eq(user));
    }

    @Test
    void loginTestReturnsTokenOnSuccess() {
        when(seckillUserDao.getById(MOBILE)).thenReturn(userWithPassword("frontPass"));

        String token = service.loginTest(response, loginVo("frontPass"));

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    private LoginVo loginVo(String formPass) {
        LoginVo vo = new LoginVo();
        vo.setMobile(String.valueOf(MOBILE));
        vo.setPassword(formPass);
        return vo;
    }

    private SeckillUser userWithPassword(String rawFormPass) {
        SeckillUser user = new SeckillUser();
        user.setId(MOBILE);
        user.setSalt(SALT);
        user.setPwd(MD5Util.formPassToDBPass(rawFormPass, SALT));
        return user;
    }
}

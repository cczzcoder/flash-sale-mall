package com.lijs.seckill.access;

import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.AccessKey;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.SeckillUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessInterceptorTest {

    private static final String TOKEN = "test-token";
    private static final String URI = "/seckill/getPath";

    @Mock
    private SeckillUserService seckillUserService;
    @Mock
    private RedisService redisService;

    private AccessInterceptor interceptor;
    private HandlerMethod limitedHandler;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        interceptor = new AccessInterceptor();
        ReflectionTestUtils.setField(interceptor, "seckillUserService", seckillUserService);
        ReflectionTestUtils.setField(interceptor, "redisService", redisService);
        limitedHandler = new HandlerMethod(this, AccessInterceptorTest.class.getDeclaredMethod("limited"));
    }

    /** 测试用限流方法：窗口 5s，上限 2 次。 */
    @SuppressWarnings("unused")
    @AccessLimit(seconds = 5, maxCount = 2)
    private void limited() {
    }

    @Test
    void firstRequestSetsCounterAndPasses() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(seckillUserService.getByToken(eq(TOKEN), any())).thenReturn(user());
        when(redisService.get(any(AccessKey.class), eq(URI + "_10001"), eq(Integer.class))).thenReturn(null);

        assertTrue(interceptor.preHandle(loggedInRequest(), response, limitedHandler));

        verify(redisService).set(any(AccessKey.class), eq(URI + "_10001"), eq(1));
        verify(redisService, never()).incr(any(AccessKey.class), anyString());
    }

    @Test
    void requestWithinLimitIncrementsCounterAndPasses() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(seckillUserService.getByToken(eq(TOKEN), any())).thenReturn(user());
        when(redisService.get(any(AccessKey.class), eq(URI + "_10001"), eq(Integer.class))).thenReturn(1);

        assertTrue(interceptor.preHandle(loggedInRequest(), response, limitedHandler));

        verify(redisService).incr(any(AccessKey.class), eq(URI + "_10001"));
    }

    @Test
    void requestOverLimitIsRejectedWithAccessLimitJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(seckillUserService.getByToken(eq(TOKEN), any())).thenReturn(user());
        when(redisService.get(any(AccessKey.class), eq(URI + "_10001"), eq(Integer.class))).thenReturn(2);

        assertFalse(interceptor.preHandle(loggedInRequest(), response, limitedHandler));

        assertTrue(response.getContentAsString()
                .contains(String.valueOf(ResultCode.ACCESS_LIMIT.getCode())));
        verify(redisService, never()).incr(any(AccessKey.class), anyString());
    }

    @Test
    void anonymousRequestIsRejectedWhenLoginRequired() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(new MockHttpServletRequest("GET", URI), response, limitedHandler));

        assertTrue(response.getContentAsString()
                .contains(String.valueOf(ResultCode.SESSION_ERROR.getCode())));
        verify(redisService, never()).get(any(), anyString(), any());
    }

    private MockHttpServletRequest loggedInRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", URI);
        request.addHeader("Authorization", TOKEN);
        return request;
    }

    private SeckillUser user() {
        SeckillUser user = new SeckillUser();
        user.setId(10001L);
        return user;
    }
}

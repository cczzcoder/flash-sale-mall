package com.lijs.seckill.access;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfInterceptorTest {

    private final CsrfInterceptor interceptor = new CsrfInterceptor();
    private final HandlerMethod handler;

    CsrfInterceptorTest() throws NoSuchMethodException {
        handler = new HandlerMethod(this, CsrfInterceptorTest.class.getDeclaredMethod("mutate"));
    }

    @SuppressWarnings("unused")
    private void mutate() {
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/address/save");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, handler));
        assertEquals(403, response.getStatus());
    }

    @Test
    void matchingCookieAndHeaderAreAccepted() throws Exception {
        String token = "csrf-test-token";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/address/save");
        request.setCookies(new Cookie(CsrfInterceptor.COOKIE_NAME, token));
        request.addHeader(CsrfInterceptor.HEADER_NAME, token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, handler));
    }
}

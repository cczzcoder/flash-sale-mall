package com.lijs.seckill.access;

import com.alibaba.fastjson.JSON;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.method.HandlerMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Double-submit CSRF protection for browser state-changing requests. */
@Service
public class CsrfInterceptor extends HandlerInterceptorAdapter {

    public static final String COOKIE_NAME = "XSRF-TOKEN";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${security.cookie.secure:false}")
    private boolean secureCookie;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String cookieToken = cookieValue(request, COOKIE_NAME);
        if (cookieToken == null || cookieToken.isEmpty()) {
            cookieToken = newToken();
            addTokenCookie(response, cookieToken);
        }

        String method = request.getMethod();
        if (!isStateChanging(method)) {
            return true;
        }

        // Non-browser API clients authenticate with Authorization and do not rely on cookies.
        if (hasText(request.getHeader("Authorization"))) {
            return true;
        }

        String headerToken = request.getHeader(HEADER_NAME);
        if (!constantTimeEquals(cookieToken, headerToken)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            render(response, ResultCode.CSRF_ERROR);
            return false;
        }
        return true;
    }

    private boolean isStateChanging(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void addTokenCookie(HttpServletResponse response, String token) {
        StringBuilder value = new StringBuilder(COOKIE_NAME).append('=').append(token)
                .append("; Path=/; SameSite=Lax");
        if (secureCookie) {
            value.append("; Secure");
        }
        response.addHeader("Set-Cookie", value.toString());
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return expected != null && actual != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private void render(HttpServletResponse response, ResultCode code) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(JSON.toJSONString(Result.error(code)).getBytes(StandardCharsets.UTF_8));
    }
}

package com.lijs.seckill.controller;

import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.result.Result;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.service.SeckillService;
import com.lijs.seckill.service.SeckillUserService;
import com.lijs.seckill.vo.LoadTestPathProvisionRequest;
import com.lijs.seckill.vo.LoadTestPathProvisionVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Local-only path provisioning for approved load-test environments.
 * This controller is absent unless the loadtest Spring profile is active.
 */
@Profile("loadtest")
@RestController
@RequestMapping("/internal/loadtest")
public class LoadTestPathController {

    private final SeckillService seckillService;
    private final SeckillUserService seckillUserService;

    @Value("${loadtest.provision.token:}")
    private String provisionToken;

    public LoadTestPathController(SeckillService seckillService,
                                  SeckillUserService seckillUserService) {
        this.seckillService = seckillService;
        this.seckillUserService = seckillUserService;
    }

    @PostMapping("/paths")
    public Result<List<LoadTestPathProvisionVo>> provision(
            HttpServletRequest request,
            @RequestHeader(value = "X-Load-Test-Token", required = false) String token,
            @RequestBody LoadTestPathProvisionRequest body) {
        if (!isLoopback(request) || !constantTimeEquals(provisionToken, token)) {
            return Result.error(ResultCode.SESSION_ERROR);
        }
        if (body == null || body.getGoodsId() == null || body.getGoodsId() <= 0
                || body.getUserIds() == null || body.getUserIds().isEmpty()
                || body.getUserIds().size() > 5000
                || new HashSet<>(body.getUserIds()).size() != body.getUserIds().size()) {
            return Result.error(ResultCode.REQUEST_ILLEGAL);
        }

        List<LoadTestPathProvisionVo> paths = new ArrayList<>(body.getUserIds().size());
        for (Long userId : body.getUserIds()) {
            if (userId == null) {
                return Result.error(ResultCode.REQUEST_ILLEGAL);
            }
            SeckillUser user = seckillUserService.getById(userId);
            if (user == null) {
                return Result.error(ResultCode.MOBILE_NOT_EXIST);
            }
            String path = seckillService.createSeckillPath(user, body.getGoodsId());
            paths.add(new LoadTestPathProvisionVo(userId, body.getGoodsId(), path));
        }
        return Result.success(paths);
    }

    private boolean isLoopback(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return "127.0.0.1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address)
                || "::1".equals(address);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}

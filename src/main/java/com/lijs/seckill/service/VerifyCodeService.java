package com.lijs.seckill.service;

import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.redis.SeckillKey;
import com.lijs.seckill.util.ArithmeticExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码服务：生成秒杀算术验证码图片（答案写入 Redis），并校验用户答案。
 * 从 SeckillService 拆出，使后者回归纯秒杀编排。
 */
@Service
public class VerifyCodeService {

    private static final char[] ops = new char[]{'+', '-', '*'};

    @Autowired
    private RedisService redisService;

    /**
     * 生成一张算术验证码图片，并将算式计算结果写入 Redis（TTL 300s）。
     */
    public BufferedImage createSeckillVerifyCode(SeckillUser user, Long goodsId) {
        if (user == null || goodsId <= 0) {
            return null;
        }
        int width = 80;
        int height = 30;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.getGraphics();
        g.setColor(new Color(0xDCDCDC));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, width - 1, height - 1);
        Random rdm = ThreadLocalRandom.current();
        for (int i = 0; i < 50; i++) {
            int x = rdm.nextInt(width);
            int y = rdm.nextInt(height);
            g.drawOval(x, y, 0, 0);
        }
        // 生成验证码
        String verifyCode = createVerifyCode(rdm);
        g.setColor(new Color(0, 100, 0));
        g.setFont(new Font("Candara", Font.BOLD, 24));
        // 将验证码写在图片上
        g.drawString(verifyCode, 8, 24);
        g.dispose();
        // 计算存值
        int rnd = calc(verifyCode);
        // 将计算结果保存到redis上面去
        redisService.set(SeckillKey.getSeckillVerifyCode, user.getId() + "_" + goodsId, rnd);
        return img;
    }

    static int calc(String exp) {
        return new ArithmeticExpression(exp).parse();
    }

    /**
     * + - *，结果保证非负：页面输入框只接受数字，负答案无法提交。
     */
    String createVerifyCode(Random rdm) {
        String expression;
        do {
            // 生成10以内的
            int n1 = rdm.nextInt(10);
            int n2 = rdm.nextInt(10);
            int n3 = rdm.nextInt(10);
            char op1 = ops[rdm.nextInt(3)]; // 0  1  2
            char op2 = ops[rdm.nextInt(3)]; // 0  1  2
            expression = "" + n1 + op1 + n2 + op2 + n3;
        } while (calc(expression) < 0);
        return expression;
    }

    /**
     * 验证验证码，取缓存里面取得值，验证是否相等
     */
    public boolean checkVCode(SeckillUser user, Long goodsId, int verifyCode) {
        Integer redisVCode = redisService.get(SeckillKey.getSeckillVerifyCode, user.getId() + "_" + goodsId, Integer.class);
        if (redisVCode == null || !redisVCode.equals(verifyCode)) {
            return false;
        }
        // 删除缓存里面的数据
        redisService.delete(SeckillKey.getSeckillVerifyCode, user.getId() + "_" + goodsId);
        return true;
    }

}

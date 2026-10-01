package com.lijs.seckill.service;

import com.lijs.seckill.domain.OrderInfo;
import com.lijs.seckill.domain.SeckillOrder;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.redis.SeckillKey;
import com.lijs.seckill.util.MD5Util;
import com.lijs.seckill.util.UUIDUtil;
import com.lijs.seckill.vo.GoodsVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

@Service
public class SeckillService {

    private static final Logger logger = LoggerFactory.getLogger(SeckillService.class);

    private static final char[] ops = new char[]{'+', '-', '*'};

    @Autowired
    private GoodsService goodsService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private RedisService redisService;

    /**
     * 一个事务原子操作【1.库存减1 2.下订单 3.写入秒杀订单】
     * 成功则返回生成的订单
     * 在执行时，数据库会给满足条件的 goods_id 这一行 加上 行锁，防止多个事务同时扣减 同一条记录。
     * 前提：数据库必须在 事务（Transaction）中 执行，才会有行锁，否则可能出现并发问题。
     */
    @Transactional
    public OrderInfo seckillWithCache(SeckillUser user, GoodsVo goodsVo) {
        return seckillWithCache(user, goodsVo, null);
    }

    @Transactional
    public OrderInfo seckillWithCache(SeckillUser user, GoodsVo goodsVo, Long deliveryAddrId) {
        // 1.减少库存
        boolean success = goodsService.reduceStock(goodsVo);
        if (success) {
            // 2.下订单（其中有两个订单: order_info、seckill_order）
            return orderService.createCacheOrder(user, goodsVo, deliveryAddrId);
        } else {
            // 减少库存失败，做一个标记，代表商品已经秒杀完了
            setGoodsOver(goodsVo.getId());
            return null;
        }
    }

    @Transactional
    public OrderInfo seckill(SeckillUser user, GoodsVo goodsvo) {
        return seckill(user, goodsvo, null);
    }

    @Transactional
    public OrderInfo seckill(SeckillUser user, GoodsVo goodsvo, Long deliveryAddrId) {
        // 1.减少库存，stock_count > 0 时才执行，受影响行数 = 0 表示库存已耗尽
        boolean success = goodsService.reduceStock(goodsvo);
        if (!success) {
            // 库存不足：标记商品售罄，不写订单，事务回滚无副作用
            setGoodsOver(goodsvo.getId());
            return null;
        }
        // 2.库存扣减成功，下订单（order_info + seckill_order）
        return orderService.createOrderWithoutCache(user, goodsvo, deliveryAddrId);
    }

    /**
     * 获取秒杀结果
     * 成功返回id
     * 失败返回0或-1
     * 0代表排队中
     * -1代表库存不足
     *
     * @param userId  用户ID
     * @param goodsId 商品ID
     * @return 秒杀结果
     */
    public long getSeckillResult(Long userId, long goodsId) {
        SeckillOrder order = orderService.getSeckillOrderByUserIdAndGoodsIdCache(userId, goodsId);
        // 秒杀成功
        if (order != null) {
            logger.info("秒杀成功：userId={}, goodsId={}, orderId:{}", userId, goodsId, order.getId());
            return order.getOrderId();
        } else {
            // 查看商品是否卖完了
            boolean isOver = getGoodsOver(goodsId);
            if (isOver) {
                // 商品卖完了
                return -1;
            } else {
                // 商品没有卖完
                return 0;
            }
        }
    }

    /**
     * 先写入缓存
     */
    private void setGoodsOver(Long goodsId) {
        redisService.set(SeckillKey.isGoodsOver, "" + goodsId, true);
    }

    private boolean getGoodsOver(Long goodsId) {
        return redisService.exitsKey(SeckillKey.isGoodsOver, "" + goodsId);
    }

    /** 订单关闭并回补库存后清除售罄标记，使恢复的库存可以重新参与秒杀。 */
    public void clearGoodsOver(Long goodsId) {
        redisService.delete(SeckillKey.isGoodsOver, "" + goodsId);
    }

    /**
     * 去缓存里面检查path是否正确，验证path。
     * 验证通过后立即删除，确保 Path 一次性有效，防止同一 Token 被重复利用。
     */
    public boolean checkPath(SeckillUser user, long goodsId, String path) {
        if (user == null || path == null) {
            return false;
        }
        String redisKey = "" + user.getId() + "_" + goodsId;
        String pathRedis = redisService.get(SeckillKey.getSeckillPath, redisKey, String.class);
        if (path.equals(pathRedis)) {
            // 验证通过，立即删除，一次性消费
            redisService.delete(SeckillKey.getSeckillPath, redisKey);
            return true;
        }
        return false;
    }

    /**
     * 生成一个秒杀path，写入缓存，并且，返回至前台
     */
    public String createSeckillPath(SeckillUser user, Long goodsId) {
        String str = MD5Util.md5(UUIDUtil.uuid() + "123456");
        // 将随机串保存在客户端，并且返回至客户端。
        redisService.set(SeckillKey.getSeckillPath, user.getId() + "_" + goodsId, str);
        return str;
    }

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
        Random rdm = new Random();
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

    /** Parses the restricted expression generated by createVerifyCode without a runtime script engine. */
    private static final class ArithmeticExpression {
        private final String expression;
        private int position;

        private ArithmeticExpression(String expression) {
            this.expression = expression;
        }

        private int parse() {
            if (expression == null || expression.isEmpty()) {
                throw new IllegalArgumentException("Verification expression is empty");
            }
            int value = parseAddition();
            if (position != expression.length()) {
                throw new IllegalArgumentException("Invalid verification expression: " + expression);
            }
            return value;
        }

        private int parseAddition() {
            int value = parseMultiplication();
            while (position < expression.length()) {
                char operator = expression.charAt(position++);
                int right = parseMultiplication();
                if (operator == '+') {
                    value += right;
                } else if (operator == '-') {
                    value -= right;
                } else {
                    position--;
                    return value;
                }
            }
            return value;
        }

        private int parseMultiplication() {
            int value = parseNumber();
            while (position < expression.length() && expression.charAt(position) == '*') {
                position++;
                value *= parseNumber();
            }
            return value;
        }

        private int parseNumber() {
            int start = position;
            while (position < expression.length() && Character.isDigit(expression.charAt(position))) {
                position++;
            }
            if (start == position) {
                throw new IllegalArgumentException("Expected number in verification expression: " + expression);
            }
            return Integer.parseInt(expression.substring(start, position));
        }
    }

    /**
     * + - *
     */
    private String createVerifyCode(Random rdm) {
        // 生成10以内的
        int n1 = rdm.nextInt(10);
        int n2 = rdm.nextInt(10);
        int n3 = rdm.nextInt(10);
        char op1 = ops[rdm.nextInt(3)]; // 0  1  2
        char op2 = ops[rdm.nextInt(3)]; // 0  1  2
        return "" + n1 + op1 + n2 + op2 + n3;
    }

    /**
     * 验证验证码，取缓存里面取得值，验证是否相等
     */
    public boolean checkVCode(SeckillUser user, Long goodsId, int verifyCode) {
        Integer redisVCode = redisService.get(SeckillKey.getSeckillVerifyCode, user.getId() + "_" + goodsId, Integer.class);
        if (redisVCode == null || redisVCode - verifyCode != 0) {
            return false;
        }
        // 删除缓存里面的数据
        redisService.delete(SeckillKey.getSeckillVerifyCode, user.getId() + "_" + goodsId);
        return true;
    }

}

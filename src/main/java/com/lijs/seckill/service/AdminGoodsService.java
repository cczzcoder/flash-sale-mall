package com.lijs.seckill.service;

import com.lijs.seckill.dao.AdminGoodsDao;
import com.lijs.seckill.domain.Goods;
import com.lijs.seckill.domain.SeckillGoods;
import com.lijs.seckill.redis.GoodsKey;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.redis.SeckillKey;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.vo.AdminGoodsVo;
import com.lijs.seckill.vo.GoodsVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.List;

@Service
public class AdminGoodsService {
    private final AdminGoodsDao goodsDao;
    private final RedisService redisService;
    private final SeckillService seckillService;

    public AdminGoodsService(AdminGoodsDao goodsDao, RedisService redisService,
                             SeckillService seckillService) {
        this.goodsDao = goodsDao;
        this.redisService = redisService;
        this.seckillService = seckillService;
    }

    public List<GoodsVo> list() { return goodsDao.list(); }

    @Transactional
    public ResultCode save(AdminGoodsVo vo) {
        if (vo.getStartDate().compareTo(vo.getEndDate()) >= 0) {
            return ResultCode.GOODS_TIME_INVALID;
        }
        if (vo.getSeckillPrice() > vo.getGoodsPrice()) {
            return ResultCode.GOODS_PRICE_INVALID;
        }
        GoodsVo current = null;
        if (vo.getGoodsId() != null) {
            current = goodsDao.get(vo.getGoodsId());
            if (current == null) return ResultCode.GOODS_NOT_EXIST;
            // 编辑商品资料不能覆盖秒杀剩余库存；库存必须走补货接口，避免重置 Redis 预扣量。
            if (!sameStock(vo.getStockCount(), current.getStockCount())) {
                return ResultCode.GOODS_STOCK_EDIT_FORBIDDEN;
            }
        }

        Goods goods = new Goods();
        goods.setId(vo.getGoodsId());
        goods.setGoodsName(vo.getGoodsName().trim());
        goods.setGoodsTitle(vo.getGoodsTitle().trim());
        goods.setGoodsImg(vo.getGoodsImg());
        goods.setGoodsDetail(vo.getGoodsDetail());
        goods.setGoodsPrice(vo.getGoodsPrice());
        goods.setGoodsStock(current == null ? vo.getStockCount() : current.getGoodsStock());
        SeckillGoods seckill = new SeckillGoods();
        seckill.setGoodsId(vo.getGoodsId());
        seckill.setSeckillPrice(vo.getSeckillPrice());
        seckill.setStockCount(current == null ? vo.getStockCount() : current.getStockCount());
        seckill.setStartDate(vo.getStartDate());
        seckill.setEndDate(vo.getEndDate());

        if (vo.getGoodsId() == null) {
            goodsDao.insertGoods(goods);
            seckill.setGoodsId(goods.getId());
            goodsDao.insertSeckill(seckill);
        } else {
            if (goodsDao.updateGoods(goods) != 1) return ResultCode.SERVER_ERROR;
            if (goodsDao.updateSeckill(seckill) != 1) return ResultCode.SERVER_ERROR;
        }
        if (current == null) {
            redisService.set(GoodsKey.getSeckillGoodsStock, String.valueOf(seckill.getGoodsId()), seckill.getStockCount());
        }
        redisService.delete(GoodsKey.getGoodsList, "");
        redisService.delete(GoodsKey.getGoodsDetail, String.valueOf(seckill.getGoodsId()));
        redisService.delete(SeckillKey.getSeckillWindow, String.valueOf(seckill.getGoodsId()));
        seckillService.clearGoodsOver(seckill.getGoodsId());
        return ResultCode.SUCCESS;
    }

    /** 只允许正向补货，DB 成功提交后再增加 Redis，避免事务回滚造成缓存虚增。 */
    @Transactional
    public ResultCode restock(long goodsId, int count) {
        if (goodsId <= 0) return ResultCode.GOODS_NOT_EXIST;
        if (count <= 0) return ResultCode.GOODS_STOCK_INVALID;
        if (goodsDao.get(goodsId) == null) return ResultCode.GOODS_NOT_EXIST;
        if (goodsDao.increaseSeckillStock(goodsId, count) != 1
                || goodsDao.increaseGoodsStock(goodsId, count) != 1) {
            return ResultCode.SERVER_ERROR;
        }
        Runnable restoreCache = () -> {
            try {
                redisService.increaseBy(GoodsKey.getSeckillGoodsStock, String.valueOf(goodsId), count);
                redisService.delete(GoodsKey.getGoodsList, "");
                redisService.delete(GoodsKey.getGoodsDetail, String.valueOf(goodsId));
                seckillService.clearGoodsOver(goodsId);
            } catch (Exception e) {
                // DB 已提交，库存对账/下次运维修复 Redis；不能回滚已提交的数据库事务。
                org.slf4j.LoggerFactory.getLogger(AdminGoodsService.class)
                        .error("补货后 Redis 同步失败 goodsId={}, count={}", goodsId, count, e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            restoreCache.run();
        } else {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    restoreCache.run();
                }
            });
        }
        return ResultCode.SUCCESS;
    }

    private boolean sameStock(Integer left, Integer right) {
        return left == null ? right == null : left.equals(right);
    }

    @Transactional
    public ResultCode delete(long goodsId) {
        if (goodsDao.get(goodsId) == null) return ResultCode.GOODS_NOT_EXIST;
        if (goodsDao.countOrders(goodsId) > 0) return ResultCode.GOODS_IN_USE;
        goodsDao.deleteSeckill(goodsId);
        goodsDao.deleteGoods(goodsId);
        redisService.delete(GoodsKey.getSeckillGoodsStock, String.valueOf(goodsId));
        redisService.delete(SeckillKey.getSeckillWindow, String.valueOf(goodsId));
        redisService.delete(GoodsKey.getGoodsDetail, String.valueOf(goodsId));
        redisService.delete(GoodsKey.getGoodsList, "");
        return ResultCode.SUCCESS;
    }
}

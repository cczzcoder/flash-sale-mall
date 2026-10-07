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

    /** 商家工作台使用：只返回指定店铺的商品。 */
    public List<GoodsVo> listByShop(long shopId) { return goodsDao.listByShopId(shopId); }

    @Transactional
    public ResultCode save(AdminGoodsVo vo) {
        return doSave(vo, null);
    }

    /**
     * 商家保存商品。scopeShopId 非空时强制归属校验：
     * 新建商品自动挂到该店铺；编辑已有商品时必须已属于该店铺，防止越权改他人商品。
     */
    @Transactional
    public ResultCode save(AdminGoodsVo vo, Long scopeShopId) {
        return doSave(vo, scopeShopId);
    }

    private ResultCode doSave(AdminGoodsVo vo, Long scopeShopId) {
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
            ResultCode scopeError = checkShopScope(current, scopeShopId);
            if (scopeError != ResultCode.SUCCESS) return scopeError;
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
        // 平台管理员新建的商品为自营（shopId=null）；商家新建自动归属其店铺
        goods.setShopId(scopeShopId);
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

    /** scopeShopId 非空时校验商品归属，平台管理员传 null 表示不限制。 */
    private ResultCode checkShopScope(GoodsVo current, Long scopeShopId) {
        if (scopeShopId != null && !scopeShopId.equals(current.getShopId())) {
            return ResultCode.GOODS_FORBIDDEN;
        }
        return ResultCode.SUCCESS;
    }

    /** 只允许正向补货，DB 成功提交后再增加 Redis，避免事务回滚造成缓存虚增。 */
    @Transactional
    public ResultCode restock(long goodsId, int count) {
        return doRestock(goodsId, count, null);
    }

    /** 商家补货：商品必须属于该店铺，防止越权给他店商品补货。 */
    @Transactional
    public ResultCode restock(long goodsId, int count, Long scopeShopId) {
        return doRestock(goodsId, count, scopeShopId);
    }

    private ResultCode doRestock(long goodsId, int count, Long scopeShopId) {
        if (goodsId <= 0) return ResultCode.GOODS_NOT_EXIST;
        if (count <= 0) return ResultCode.GOODS_STOCK_INVALID;
        GoodsVo current = goodsDao.get(goodsId);
        if (current == null) return ResultCode.GOODS_NOT_EXIST;
        ResultCode scopeError = checkShopScope(current, scopeShopId);
        if (scopeError != ResultCode.SUCCESS) return scopeError;
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
        return doDelete(goodsId, null);
    }

    /** 商家删除商品：商品必须属于该店铺，防止越权删除他店商品。 */
    @Transactional
    public ResultCode delete(long goodsId, Long scopeShopId) {
        return doDelete(goodsId, scopeShopId);
    }

    private ResultCode doDelete(long goodsId, Long scopeShopId) {
        GoodsVo current = goodsDao.get(goodsId);
        if (current == null) return ResultCode.GOODS_NOT_EXIST;
        ResultCode scopeError = checkShopScope(current, scopeShopId);
        if (scopeError != ResultCode.SUCCESS) return scopeError;
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

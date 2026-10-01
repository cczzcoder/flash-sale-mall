package com.lijs.seckill.service;

import com.lijs.seckill.dao.GoodsDao;
import com.lijs.seckill.domain.SeckillGoods;
import com.lijs.seckill.vo.GoodsVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品服务，封装商品查询和秒杀库存扣减逻辑。
 */
@Service
public class GoodsService {

    private final Logger logger = LoggerFactory.getLogger(GoodsService.class);

    @Autowired
    private GoodsDao goodsDao;

    /**
     * 查询所有参与秒杀的商品列表（关联 goods + seckill_goods 的联合视图）。
     *
     * @return GoodsVo 列表，包含商品基本信息和秒杀价格、库存、时间等
     */
    public List<GoodsVo> getGoodsVoList() {
        return goodsDao.getGoodsVoList();
    }

    /**
     * 根据商品 ID 查询单个商品的完整视图信息。
     *
     * @param goodsId 商品 ID
     * @return GoodsVo，包含商品信息 + 秒杀信息；不存在则返回 null
     */
    public GoodsVo getGoodsVoByGoodsId(long goodsId) {
        return goodsDao.getGoodsVoByGoodsId(goodsId);
    }

    /**
     * 扣减秒杀商品库存（UPDATE seckill_goods SET stock_count = stock_count - 1 WHERE goods_id = ? AND stock_count > 0）。
     *
     * <p>SQL 中带 {@code stock_count > 0} 的条件，数据库层面防止库存扣成负数（防超卖兜底）。
     * 受影响行数 > 0 表示扣减成功；= 0 表示库存已耗尽。
     *
     * <p>调用方（{@link SeckillService#seckillWithCache}）会根据返回值决定是否继续写订单，
     * 库存扣减失败时不写订单，避免脏数据。
     *
     * @param goodsVo 商品视图对象（通过 goodsId 定位要扣减的行）
     * @return true 表示扣减成功（库存充足）；false 表示库存不足，扣减失败
     */
    public boolean reduceStock(GoodsVo goodsVo) {
        SeckillGoods goods = new SeckillGoods();
        goods.setGoodsId(goodsVo.getId());
        int ret = goodsDao.reduceStock(goods);
        logger.info("reduceStock result: {}", ret);
        return ret > 0;
    }

    /** 订单关闭时恢复数据库库存；调用方必须位于关单事务内。 */
    public void increaseStock(long goodsId, int count) {
        if (count <= 0 || goodsDao.increaseStock(goodsId, count) != 1) {
            throw new IllegalStateException("恢复数据库库存失败 goodsId=" + goodsId + ", count=" + count);
        }
    }
}

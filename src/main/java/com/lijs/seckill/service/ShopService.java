package com.lijs.seckill.service;

import com.lijs.seckill.dao.SeckillUserDao;
import com.lijs.seckill.dao.ShopDao;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.domain.Shop;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.redis.SeckillUserKey;
import com.lijs.seckill.result.ResultCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 店铺服务：商家入驻申请 + 平台审核。
 *
 * <p>入驻流程：用户提交申请（shop.status=0 待审核）→ 管理员审核通过
 * （shop.status=1 营业中，同时授予 seckill_user.role=1 商家角色）或停用（status=2）。
 */
@Service
public class ShopService {

    private final ShopDao shopDao;
    private final SeckillUserDao seckillUserDao;
    private final RedisService redisService;

    public ShopService(ShopDao shopDao, SeckillUserDao seckillUserDao, RedisService redisService) {
        this.shopDao = shopDao;
        this.seckillUserDao = seckillUserDao;
        this.redisService = redisService;
    }

    /** 提交入驻申请：一个用户只能有一个店铺（uk_owner_user 兜底并发重复提交）。 */
    @Transactional
    public ResultCode apply(long userId, String name, String logo, String description) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            return ResultCode.SHOP_NAME_INVALID;
        }
        if (shopDao.selectByOwnerUserId(userId) != null) {
            return ResultCode.SHOP_ALREADY_APPLIED;
        }
        Shop shop = new Shop();
        shop.setOwnerUserId(userId);
        shop.setName(trimmedName);
        shop.setLogo(logo == null || logo.trim().isEmpty() ? null : logo.trim());
        shop.setDescription(description == null || description.trim().isEmpty() ? null : description.trim());
        shop.setStatus(Shop.STATUS_PENDING);
        try {
            shopDao.insert(shop);
        } catch (DuplicateKeyException e) {
            return ResultCode.SHOP_ALREADY_APPLIED;
        }
        return ResultCode.SUCCESS;
    }

    /** 当前用户的店铺（未入驻返回 null），商家工作台据此展示不同面板。 */
    public Shop getByOwner(long userId) {
        return shopDao.selectByOwnerUserId(userId);
    }

    public List<Shop> listAll() {
        return shopDao.listAll();
    }

    /**
     * 平台审核：status 只允许 1（通过/启用）或 2（停用）。
     * 通过时授予店主商家角色，已授予的重复操作幂等。
     */
    @Transactional
    public ResultCode review(long shopId, int status) {
        if (status != Shop.STATUS_ACTIVE && status != Shop.STATUS_DISABLED) {
            return ResultCode.SHOP_STATUS_INVALID;
        }
        Shop shop = shopDao.selectById(shopId);
        if (shop == null) {
            return ResultCode.SHOP_NOT_EXIST;
        }
        if (shopDao.updateStatus(shopId, status) != 1) {
            return ResultCode.SERVER_ERROR;
        }
        if (status == Shop.STATUS_ACTIVE) {
            seckillUserDao.updateRole(shop.getOwnerUserId(), SeckillUser.ROLE_MERCHANT);
            // 角色变更后失效用户缓存，使商家身份即时生效（getById 缓存永不过期）
            redisService.delete(SeckillUserKey.getById, String.valueOf(shop.getOwnerUserId()));
        }
        return ResultCode.SUCCESS;
    }
}

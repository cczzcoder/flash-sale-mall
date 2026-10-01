package com.lijs.seckill.service;

import com.lijs.seckill.dao.SeckillUserDao;
import com.lijs.seckill.domain.SeckillUser;
import com.lijs.seckill.redis.RedisService;
import com.lijs.seckill.redis.SeckillUserKey;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.util.MD5Util;
import com.lijs.seckill.util.UUIDUtil;
import com.lijs.seckill.vo.LoginVo;
import com.lijs.seckill.vo.RegisterVo;
import com.lijs.seckill.vo.ChangePasswordVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * 秒杀用户服务，处理用户登录、token 管理和用户信息缓存。
 *
 * <p><b>分布式 Session 方案：</b>
 * 登录成功后生成随机 UUID 作为 token，以 {@code SeckillUserKey.token:token → SeckillUser}
 * 的形式存入 Redis，同时将 token 写入浏览器 Cookie。
 * 后续请求携带 Cookie 中的 token，服务端从 Redis 还原用户，实现无状态的分布式 Session。
 *
 * <p><b>密码两次 MD5 策略：</b>
 * <ol>
 *   <li>前端：inputPass + 固定 salt → formPass（防止密码明文在网络传输）</li>
 *   <li>服务端：formPass + 数据库随机 salt → dbPass（防止数据库泄露后彩虹表攻击）</li>
 * </ol>
 */
@Service
public class SeckillUserService {

    /** Cookie 和 Redis key 中 token 的名称常量，供拦截器和 Resolver 统一引用 */
    public static final String COOKIE_NAME_TOKEN = "token";

    @Autowired
    private SeckillUserDao seckillUserDao;
    @Autowired
    private RedisService redisService;

    /** Secure must be enabled when the application is served over HTTPS. */
    @Value("${security.cookie.secure:false}")
    private boolean secureCookie;

    /**
     * 根据用户 ID 查询用户，先查 Redis 缓存，缓存未命中再查数据库并回写缓存。
     *
     * @param id 用户 ID（手机号）
     * @return 用户对象，不存在则返回 null
     */
    public SeckillUser getById(long id) {
        // 1. 先查 Redis 缓存（key = SeckillUserKey.getById:id）
        SeckillUser user = redisService.get(SeckillUserKey.getById, "" + id, SeckillUser.class);
        if (user != null) {
            return user;
        }
        // 2. 缓存未命中，查数据库
        user = seckillUserDao.getById(id);
        if (user != null) {
            // 3. 回写缓存，下次直接命中
            redisService.set(SeckillUserKey.getById, "" + id, user);
        }
        return user;
    }

    /**
     * 根据 token 获取用户信息，同时顺延 Cookie 和 Redis 中 token 的有效期。
     *
     * <p>每次请求都调用此方法，相当于"滑动窗口"续期：只要用户在有效期内有操作，
     * token 就不会过期。
     *
     * @param token    从 Cookie / URL 参数 / Header 中提取的 token
     * @param response HTTP 响应，用于更新 Set-Cookie 头
     * @return 用户对象；token 为空或 Redis 中不存在则返回 null
     */
    public SeckillUser getByToken(String token, HttpServletResponse response) {
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        SeckillUser user = redisService.get(SeckillUserKey.token, token, SeckillUser.class);
        // 用户存在时重新设置 Cookie，实现有效期滑动续期
        if (user != null) {
            addCookie(user, token, response);
        }
        return user;
    }

    /**
     * 测试用登录，登录成功直接返回 token 字符串（方便 Postman / JMeter 调试）。
     *
     * @param response HTTP 响应，用于写 Cookie
     * @param loginVo  登录参数（手机号 + 前端一次 MD5 后的密码）
     * @return 登录成功返回 token；失败返回错误信息字符串
     */
    public String loginTest(HttpServletResponse response, LoginVo loginVo) {
        if (loginVo == null) {
            return ResultCode.SERVER_ERROR.getMsg();
        }
        String mobile   = loginVo.getMobile();
        String password = loginVo.getPassword();

        // 按手机号查用户（手机号即用户 ID）
        SeckillUser user = getById(Long.parseLong(mobile));
        if (user == null) {
            return ResultCode.MOBILE_NOT_EXIST.getMsg();
        }
        // 用数据库中的随机 salt 对 formPass 做第二次 MD5，与 dbPass 比对
        String dbPass  = user.getPwd();
        String dbSalt  = user.getSalt();
        String tmpPass = MD5Util.formPassToDBPass(password, dbSalt);
        if (!tmpPass.equals(dbPass)) {
            return ResultCode.PASSWORD_ERROR.getMsg();
        }
        // 生成 token 并写入 Cookie
        String token = UUIDUtil.uuid();
        addCookie(user, token, response);
        return token;
    }

    /**
     * 正式登录，验证手机号与密码，成功后生成 token 写入 Cookie。
     *
     * @param response HTTP 响应，用于写 Cookie
     * @param loginVo  登录参数
     * @return {@link ResultCode#SUCCESS} 表示成功；其他枚举值表示具体错误
     */
    public ResultCode login(HttpServletResponse response, LoginVo loginVo) {
        if (loginVo == null) {
            return ResultCode.SERVER_ERROR;
        }
        String mobile   = loginVo.getMobile();
        String formPass = loginVo.getPassword();

        SeckillUser user = getById(Long.parseLong(mobile));
        if (user == null) {
            return ResultCode.MOBILE_NOT_EXIST;
        }
        // 二次 MD5 验证：formPass + dbSalt → tmpPass，与库中 dbPass 对比
        String dbPass  = user.getPwd();
        String dbSalt  = user.getSalt();
        String tmpPass = MD5Util.formPassToDBPass(formPass, dbSalt);
        if (!tmpPass.equals(dbPass)) {
            return ResultCode.PASSWORD_ERROR;
        }
        String token = UUIDUtil.uuid();
        addCookie(user, token, response);
        return ResultCode.SUCCESS;
    }

    public ResultCode register(RegisterVo registerVo) {
        if (registerVo == null) {
            return ResultCode.SERVER_ERROR;
        }
        long userId;
        try {
            userId = Long.parseLong(registerVo.getMobile());
        } catch (RuntimeException e) {
            return ResultCode.MOBILE_ERROR;
        }
        if (getById(userId) != null) {
            return ResultCode.MOBILE_EXISTS;
        }
        SeckillUser user = new SeckillUser();
        user.setId(userId);
        user.setNickname(registerVo.getNickname().trim());
        user.setSalt(UUIDUtil.uuid().substring(0, 8));
        user.setPwd(MD5Util.formPassToDBPass(registerVo.getPassword(), user.getSalt()));
        user.setRegisterDate(new java.util.Date());
        user.setLoginCount(0);
        try {
            seckillUserDao.insert(user);
        } catch (DuplicateKeyException e) {
            return ResultCode.MOBILE_EXISTS;
        }
        return ResultCode.SUCCESS;
    }

    public ResultCode changePassword(long userId, ChangePasswordVo changePasswordVo) {
        if (changePasswordVo == null) {
            return ResultCode.SERVER_ERROR;
        }
        SeckillUser user = getById(userId);
        if (user == null) {
            return ResultCode.SESSION_ERROR;
        }
        String current = MD5Util.formPassToDBPass(changePasswordVo.getOldPassword(), user.getSalt());
        if (!current.equals(user.getPwd())) {
            return ResultCode.PASSWORD_ERROR;
        }
        user.setSalt(UUIDUtil.uuid().substring(0, 8));
        user.setPwd(MD5Util.formPassToDBPass(changePasswordVo.getNewPassword(), user.getSalt()));
        if (seckillUserDao.updatePassword(user) != 1) {
            return ResultCode.SERVER_ERROR;
        }
        redisService.delete(SeckillUserKey.getById, String.valueOf(userId));
        return ResultCode.SUCCESS;
    }

    public void logout(String token, HttpServletResponse response) {
        if (!StringUtils.isEmpty(token)) {
            redisService.delete(SeckillUserKey.token, token);
        }
        Cookie cookie = new Cookie(COOKIE_NAME_TOKEN, "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        response.addCookie(cookie);
    }

    /**
     * 将 token 存入 Redis 并设置同名 Cookie。
     * Redis key：{@code SeckillUserKey.token:token} → value：SeckillUser JSON。
     * Cookie 有效期与 Redis TTL 保持一致（{@link SeckillUserKey#token#expireSeconds()}）。
     *
     * <p>可复用旧 token：无需每次都生成新 UUID，直接用已有 token 续期即可。
     *
     * @param user  当前用户对象
     * @param token 要写入 Redis 和 Cookie 的 token
     */
    public void addCookie(SeckillUser user, String token, HttpServletResponse response) {
        // 将 token → user 的映射写入 Redis，后续凭 token 取用户
        redisService.set(SeckillUserKey.token, token, user);
        Cookie cookie = new Cookie(COOKIE_NAME_TOKEN, token);
        // Cookie 有效期与 Redis 中的 session 有效期保持一致
        cookie.setMaxAge(SeckillUserKey.token.expireSeconds());
        // 设置路径为根路径，整个站点的请求都会携带此 Cookie
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        response.addCookie(cookie);
    }
}

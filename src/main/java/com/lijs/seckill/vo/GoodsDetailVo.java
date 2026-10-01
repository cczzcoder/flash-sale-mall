package com.lijs.seckill.vo;

import com.lijs.seckill.domain.SeckillUser;

/**
 * 商品详情页数据 VO，用于前后端分离（静态化）场景的 JSON 接口响应。
 *
 * <p>对应接口：{@link com.lijs.seckill.controller.GoodsController#detailStaticPage}
 *
 * <p>前端加载静态 HTML 后，Ajax 调用该接口获取此 VO，然后用 JS 将各字段渲染到页面：
 * <ul>
 *   <li>goodsVo         — 商品信息（名称、图片、原价、秒杀价、库存、时间等）</li>
 *   <li>status          — 秒杀状态（0=未开始 1=进行中 2=已结束），前端据此显示按钮/倒计时</li>
 *   <li>remainingSeconds — 距秒杀开始的倒计时秒数；进行中=0，已结束=-1</li>
 *   <li>user            — 当前登录用户信息，未登录时为 null</li>
 * </ul>
 */
public class GoodsDetailVo {

    /** 秒杀状态：0=未开始 1=进行中 2=已结束 */
    private int status = 0;
    /** 距秒杀开始的倒计时（秒）；进行中=0，已结束=-1 */
    private int remainingSeconds = 0;
    /** 商品+秒杀活动信息 */
    private GoodsVo goodsVo;
    /** 当前登录用户，未登录时为 null */
    private UserInfoVo user;

    public int        getStatus()                      { return status; }
    public void       setStatus(int status)            { this.status = status; }
    public int        getremainingSeconds()            { return remainingSeconds; }
    public void       setremainingSeconds(int v)       { this.remainingSeconds = v; }
    public GoodsVo    getGoodsVo()                     { return goodsVo; }
    public void       setGoodsVo(GoodsVo goodsVo)     { this.goodsVo = goodsVo; }
    public UserInfoVo getUser()                       { return user; }
    public void        setUser(SeckillUser user)       { this.user = UserInfoVo.from(user); }
}

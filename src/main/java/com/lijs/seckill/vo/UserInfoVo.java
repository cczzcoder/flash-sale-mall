package com.lijs.seckill.vo;

import com.lijs.seckill.domain.SeckillUser;

import java.util.Date;

/** Public user representation; credential fields are intentionally omitted. */
public class UserInfoVo {

    private final Long id;
    private final String nickname;
    private final String head;
    private final Date registerDate;
    private final Date lastLoginDate;
    private final Integer loginCount;

    private UserInfoVo(SeckillUser user) {
        this.id = user.getId();
        this.nickname = user.getNickname();
        this.head = user.getHead();
        this.registerDate = user.getRegisterDate();
        this.lastLoginDate = user.getLastLoginDate();
        this.loginCount = user.getLoginCount();
    }

    public static UserInfoVo from(SeckillUser user) {
        return user == null ? null : new UserInfoVo(user);
    }

    public Long getId() { return id; }
    public String getNickname() { return nickname; }
    public String getHead() { return head; }
    public Date getRegisterDate() { return registerDate; }
    public Date getLastLoginDate() { return lastLoginDate; }
    public Integer getLoginCount() { return loginCount; }
}

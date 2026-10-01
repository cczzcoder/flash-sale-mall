package com.lijs.seckill.vo;

import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;
import com.lijs.seckill.util.IsMobile;

/**
 * 登录请求参数 VO（Value Object）。
 *
 * <p>字段校验规则（由 JSR-303 / Hibernate Validator 在 Controller 层通过 @Valid 触发）：
 * <ul>
 *   <li>mobile   — 不能为 null，且必须符合手机号格式（@IsMobile 自定义注解）</li>
 *   <li>password — 不能为 null，且长度至少 32 位（前端已做一次 MD5，MD5 结果为 32 位十六进制字符串）</li>
 * </ul>
 *
 * <p>注意：password 字段存储的是前端经过第一次 MD5 处理后的值（formPass），
 * 并非用户输入的原始密码，服务端拿到后还需用数据库 salt 再做一次 MD5 验证。
 * 详见 {@link com.lijs.seckill.util.MD5Util}。
 */
public class LoginVo {

    /** 手机号，同时作为用户 ID 使用 */
    private String mobile;

    /** 前端一次 MD5 后的密码（formPass），长度固定 32 位 */
    private String password;

    @NotNull
    @IsMobile  // 自定义手机号格式校验注解，见 IsMobile + IsMobileValidator
    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    @NotNull
    @Length(min = 32)  // formPass 是 32 位 MD5 字符串，短于 32 位说明前端未做 MD5
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

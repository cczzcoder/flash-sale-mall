package com.lijs.seckill.vo;

import com.lijs.seckill.util.IsMobile;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** Registration request. Password is the same client-side MD5 form value used by LoginVo. */
public class RegisterVo {

    private String mobile;
    private String password;
    private String nickname;

    @NotNull
    @IsMobile
    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    @NotNull
    @Length(min = 32, max = 32)
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @NotBlank
    @Size(max = 64)
    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}

package com.lijs.seckill.vo;

import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;

/** Password change request; both values are client-side MD5 form values. */
public class ChangePasswordVo {

    private String oldPassword;
    private String newPassword;

    @NotNull
    @Length(min = 32, max = 32)
    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    @NotNull
    @Length(min = 32, max = 32)
    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}

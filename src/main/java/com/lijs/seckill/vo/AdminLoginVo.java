package com.lijs.seckill.vo;

import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;

public class AdminLoginVo {
    @NotBlank
    private String username;
    @NotBlank
    @Length(min = 32, max = 32)
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

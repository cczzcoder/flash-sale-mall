package com.lijs.seckill.vo;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** Profile update request; only display fields are mutable. */
public class ProfileVo {

    private String nickname;
    private String head;

    @NotBlank
    @Size(max = 64)
    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 头像图片地址：站内相对路径（/ 开头）或 http(s) 绝对地址，留空使用默认头像 */
    @Size(max = 128)
    @Pattern(regexp = "^$|^(/|https?://).*", message = "头像地址需以 / 或 http(s):// 开头")
    public String getHead() {
        return head;
    }

    public void setHead(String head) {
        this.head = head;
    }
}

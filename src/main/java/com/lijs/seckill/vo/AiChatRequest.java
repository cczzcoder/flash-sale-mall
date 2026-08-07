package com.lijs.seckill.vo;

public class AiChatRequest {

    private String message;
    /** 语言代码：zh（中文）/ en（English）/ ja（日本語） */
    private String lang;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
}

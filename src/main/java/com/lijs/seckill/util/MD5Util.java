package com.lijs.seckill.util;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * MD5 工具类，实现两次加盐 MD5 的密码处理策略。
 *
 * <p><b>密码安全设计：</b>
 * 为防止密码在网络传输中明文暴露，同时防止数据库泄露后彩虹表攻击，采用两次加盐 MD5：
 * <pre>
 * 第一次（前端完成，固定 salt）：
 *   inputPass（用户输入的原始密码）+ 固定 salt  →  formPass（MD5 后通过表单提交）
 *
 * 第二次（服务端完成，随机 salt）：
 *   formPass + 数据库随机 salt  →  dbPass（存入数据库的密码）
 * </pre>
 *
 * <p>salt 拼接规则：取 salt 的第 0、2 位拼在密码前，第 5、4 位拼在密码后，
 * 再整体做 MD5，增加破解难度。
 */
public class MD5Util {

    /** 对字符串做 MD5，返回 32 位小写十六进制字符串 */
    public static String md5(String src) {
        return DigestUtils.md5Hex(src);
    }

    /**
     * 前端固定 salt，与用户输入的原始密码拼装后做第一次 MD5。
     * 此 salt 前后端共享，写死在代码中（生产环境建议配置化）。
     */
    private static final String salt = "1a2b3c4d";

    /**
     * 第一次 MD5：inputPass → formPass（前端调用）。
     * 拼接规则：salt[0] + salt[2] + inputPass + salt[5] + salt[4]
     *
     * @param inputPass 用户在登录框输入的原始密码
     * @return formPass，提交到服务端的密码（已做一次 MD5）
     */
    public static String inputPassToFormPass(String inputPass) {
        String str = "" + salt.charAt(0) + salt.charAt(2) + inputPass + salt.charAt(5) + salt.charAt(4);
        return md5(str);
    }

    /**
     * 第二次 MD5：formPass + 随机 salt → dbPass（服务端调用）。
     * 拼接规则：salt[0] + salt[2] + formPass + salt[5] + salt[4]
     *
     * @param formPass 前端传来的一次 MD5 密码
     * @param salt     数据库中该用户的随机 salt
     * @return dbPass，与数据库存储值比对的密码
     */
    public static String formPassToDBPass(String formPass, String salt) {
        String str = "" + salt.charAt(0) + salt.charAt(2) + formPass + salt.charAt(5) + salt.charAt(4);
        return md5(str);
    }

    /**
     * 两次 MD5 合并：inputPass + dbSalt → dbPass（测试/初始化数据时使用）。
     *
     * @param input  用户输入的原始密码
     * @param saltDB 数据库中的随机 salt
     * @return 最终存入数据库的密码
     */
    public static String inputPassToDbPass(String input, String saltDB) {
        String formPass = inputPassToFormPass(input);
        return formPassToDBPass(formPass, saltDB);
    }

}

package com.lijs.seckill.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * 通用参数校验工具类。
 * 目前只包含手机号格式验证，供 {@link IsMobileValidator} 调用。
 */
public class ValidatorUtil {

    /**
     * 手机号正则：1 开头，第二位为 3-9，后跟 9 位数字，共 11 位。
     * 覆盖目前国内主流号段（13x、14x、15x、16x、17x、18x、19x）。
     */
    public static final String REG_MOBILE_TELEPHONE = "^(1[3-9])\\d{9}$";

    /** 预编译正则，避免每次调用都重新编译，提升性能 */
    public static final Pattern MOBILE_TELEPHONE_PATTERN = Pattern.compile(REG_MOBILE_TELEPHONE);

    /**
     * 验证手机号格式是否合法。
     *
     * @param src 待验证的字符串
     * @return true 表示合法的手机号格式；null / 空字符串 / 格式不符均返回 false
     */
    public static boolean isMobile(String src) {
        if (StringUtils.isEmpty(src)) {
            return false;
        }
        Matcher m = MOBILE_TELEPHONE_PATTERN.matcher(src);
        return m.matches();
    }
}

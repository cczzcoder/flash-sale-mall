package com.lijs.seckill.util;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

/**
 * {@link IsMobile} 注解的实现类，负责实际的手机号格式校验逻辑。
 *
 * <p>JSR-303 规范要求：实现 {@link ConstraintValidator}&lt;注解类型, 被校验值类型&gt;，
 * 并在 {@link IsMobile} 的 {@code @Constraint(validatedBy)} 中声明本类。
 *
 * <p>校验规则：
 * <ul>
 *   <li>{@code required = true}（默认）：value 为空或格式不合法都返回 false</li>
 *   <li>{@code required = false}：value 为空时返回 true（跳过校验）；不为空时仍验证格式</li>
 * </ul>
 */
public class IsMobileValidator implements ConstraintValidator<IsMobile, String> {

    /** 对应 @IsMobile(required = ?) 的值，initialize 时从注解读取 */
    private boolean required = false;

    /**
     * 初始化方法，在校验器被创建时调用一次。
     * 从注解属性中读取 required 配置。
     */
    @Override
    public void initialize(IsMobile constraintAnnotation) {
        required = constraintAnnotation.required();
    }

    /**
     * 执行实际校验，由 JSR-303 框架在触发 @Valid 时调用。
     *
     * @param value   被校验的手机号字符串
     * @param context 校验上下文（可用于自定义错误信息，此处未使用）
     * @return true 表示校验通过；false 表示不合法，框架会抛出 ConstraintViolationException
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (required) {
            // required = true：空值也不合法
            return ValidatorUtil.isMobile(value);
        } else {
            // required = false：空值视为合法（跳过），非空才验证格式
            if (StringUtils.isEmpty(value)) {
                return true;
            } else {
                return ValidatorUtil.isMobile(value);
            }
        }
    }
}

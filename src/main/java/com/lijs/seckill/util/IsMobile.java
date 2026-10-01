package com.lijs.seckill.util;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

/**
 * 自定义手机号格式校验注解，基于 JSR-303（Bean Validation）规范。
 *
 * <p>用法：标注在 getter 方法或字段上，配合 @Valid 触发校验。
 * 实际校验逻辑由 {@link IsMobileValidator} 实现。
 *
 * <p>示例（{@link com.lijs.seckill.vo.LoginVo}）：
 * <pre>{@code
 * @NotNull
 * @IsMobile
 * public String getMobile() { ... }
 * }</pre>
 *
 * <p>{@code required} 参数：
 * <ul>
 *   <li>true（默认）：值不能为空且必须符合手机号格式</li>
 *   <li>false：值为空时跳过校验；不为空时仍验证格式</li>
 * </ul>
 */
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = {IsMobileValidator.class}) // 指定负责实际校验的类
public @interface IsMobile {

    /** 是否必填，true 表示不允许为空且必须为合法手机号 */
    boolean required() default true;

    /** 校验失败时的默认错误提示信息 */
    String message() default "手机号码格式有误!";

    /** 分组校验（JSR-303 标准属性，通常留空） */
    Class<?>[] groups() default {};

    /** 载荷（JSR-303 标准属性，用于携带元数据，通常留空） */
    Class<? extends Payload>[] payload() default {};
}

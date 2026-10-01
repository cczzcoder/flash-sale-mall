package com.lijs.seckill.exception;

import com.lijs.seckill.result.ResultCode;

/**
 * 业务异常，携带 {@link ResultCode} 错误码。
 *
 * <p>用于在 Service / 深层业务逻辑中快速抛出带有业务语义的异常，
 * 由 {@link GlobalExceptionHandler} 统一捕获并转换为 JSON 响应。
 *
 * <p>使用示例：
 * <pre>{@code
 * if (user == null) {
 *     throw new GlobalException(ResultCode.SESSION_ERROR);
 * }
 * }</pre>
 *
 * <p>继承 {@link RuntimeException}，无需在方法签名上声明 throws，
 * 调用链上任意层级抛出后都会被 @ControllerAdvice 捕获。
 */
public class GlobalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 携带的业务错误码，包含 code 和 msg */
    private ResultCode cm;

    /**
     * @param cm 业务错误码，最终会被序列化为 JSON 中的 code 和 msg
     */
    public GlobalException(ResultCode cm) {
        super(cm.toString());
        this.cm = cm;
    }

    public ResultCode getCm()            { return cm; }
    public void       setCm(ResultCode cm) { this.cm = cm; }
}

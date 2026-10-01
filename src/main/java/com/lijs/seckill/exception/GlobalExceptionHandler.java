package com.lijs.seckill.exception;

import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 全局异常处理器，拦截所有 Controller 抛出的异常并统一转换为 JSON 响应。
 *
 * <p>{@code @ControllerAdvice} 使其作用于所有 @Controller，
 * 配合 {@code @ResponseBody} 将返回值直接序列化为 JSON 写入响应体。
 *
 * <p>处理逻辑：
 * <ol>
 *   <li>{@link GlobalException} — 业务异常，直接取其 {@link ResultCode} 返回</li>
 *   <li>{@link BindException}  — 参数校验失败（@Valid 触发），提取第一条校验错误信息，
 *       填充到 {@link ResultCode#BIND_ERROR} 的 %s 占位符中返回</li>
 *   <li>其他异常              — 统一返回 SERVER_ERROR（500）</li>
 * </ol>
 */
@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 拦截所有异常，按类型分别处理后统一包装为 {@link Result} 返回。
     *
     * @param request HTTP 请求（可用于记录日志、获取请求路径等）
     * @param e       捕获到的异常
     */
    @ExceptionHandler(value = Exception.class)
    public Result<String> exceptionHandler(HttpServletRequest request, Exception e) {
        if (e instanceof GlobalException) {
            // 业务异常：直接使用其中携带的 ResultCode
            GlobalException ex = (GlobalException) e;
            ResultCode cm = ex.getCm();
            return Result.error(cm);
        }
        if (e instanceof BindException) {
            // 参数校验异常（@Valid 校验失败）：提取第一条错误描述填入 BIND_ERROR 的占位符
            BindException ex = (BindException) e;
            List<ObjectError> errors = ex.getAllErrors();
            ObjectError error = errors.get(0);
            String msg = error.getDefaultMessage();
            return Result.error(ResultCode.BIND_ERROR.fillArgs(msg));
        } else {
            // 未知异常必须留在服务端日志中，响应仍隐藏内部细节
            logger.error("未处理异常 method={} uri={}", request.getMethod(), request.getRequestURI(), e);
            return Result.error(ResultCode.SERVER_ERROR);
        }
    }
}

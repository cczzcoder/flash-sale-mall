package com.lijs.seckill.result;

/**
 * 统一 API 响应包装类，泛型 T 为业务数据类型。
 *
 * <p>约定：
 * <ul>
 *   <li>成功：code = 0，msg = "success"，data = 业务数据</li>
 *   <li>失败：code = 非零错误码，msg = 错误描述，data = null</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 成功
 * return Result.success(orderVo);
 * // 失败
 * return Result.error(ResultCode.SESSION_ERROR);
 * }</pre>
 */
public class Result<T> {

    /** 业务状态码，0 表示成功，非 0 表示具体错误（见 ResultCode） */
    private int code;
    /** 提示信息，成功时为 "success"，失败时为错误描述 */
    private String msg;
    /** 业务数据，失败时为 null */
    private T data;

    /** 成功时的私有构造，code 固定为 0 */
    private Result(T data) {
        this.code = 0;
        this.msg = "success";
        this.data = data;
    }

    /** 失败时的私有构造，从 ResultCode 中取错误码和错误信息 */
    private Result(ResultCode resultCode) {
        if (resultCode == null) {
            return;
        }
        this.code = resultCode.getCode();
        this.msg  = resultCode.getMsg();
    }

    /**
     * 构建成功响应。
     *
     * @param data 要返回给前端的业务数据
     */
    public static <T> Result<T> success(T data) {
        return new Result<T>(data);
    }

    /**
     * 构建失败响应。
     *
     * @param resultCode 错误码枚举，包含错误码和错误描述
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<T>(resultCode);
    }

    public int getCode()         { return code; }
    public void setCode(int code){ this.code = code; }
    public String getMsg()       { return msg; }
    public void setMsg(String msg){ this.msg = msg; }
    public T getData()           { return data; }
    public void setData(T data)  { this.data = data; }
}

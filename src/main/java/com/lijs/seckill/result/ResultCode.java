package com.lijs.seckill.result;

/**
 * 业务错误码定义。
 *
 * <p>编码规范（参考 HTTP 状态码风格）：
 * <ul>
 *   <li>0       — 成功</li>
 *   <li>5001xx  — 通用服务端错误</li>
 *   <li>5002xx  — 用户/认证相关错误</li>
 *   <li>5004xx  — 订单相关错误</li>
 *   <li>5005xx  — 秒杀业务相关错误</li>
 * </ul>
 *
 * <p>带占位符的 msg（如 {@code "参数校验异常:%s"}）可通过 {@link #fillArgs(Object...)} 填充动态内容。
 */
public class ResultCode {

    private int code;
    private String msg;

    // ----------------------------- 通用 -----------------------------
    public static ResultCode SUCCESS         = new ResultCode(0,      "success");
    public static ResultCode SERVER_ERROR    = new ResultCode(500100, "服务端异常!");
    /** 参数校验失败，msg 中 %s 占位符由 fillArgs 填充具体字段名 */
    public static ResultCode BIND_ERROR      = new ResultCode(500101, "参数校验异常:%s");
    public static ResultCode REQUEST_ILLEGAL = new ResultCode(500102, "非法请求!");
    public static ResultCode SECKILL_FAIL    = new ResultCode(500103, "秒杀失败!");
    /** 接口访问频率超限（由 AccessInterceptor 根据 @AccessLimit 注解触发） */
    public static ResultCode ACCESS_LIMIT    = new ResultCode(500104, "达到访问限制次数，访问太频繁!");

    // ----------------------------- 用户/认证 -------------------------
    /** Token 失效或未登录（Redis 中 session 不存在） */
    public static ResultCode SESSION_ERROR     = new ResultCode(500210, "session失效!");
    public static ResultCode PASSWORD_EMPTY    = new ResultCode(500211, "密码不能为空!");
    public static ResultCode MOBILE_EMPTY      = new ResultCode(500212, "手机号不能为空!");
    public static ResultCode MOBILE_ERROR      = new ResultCode(500213, "手机号格式错误!");
    public static ResultCode MOBILE_NOT_EXIST  = new ResultCode(500214, "手机号号码不存在!");
    public static ResultCode PASSWORD_ERROR    = new ResultCode(500215, "密码错误!");
    public static ResultCode MOBILE_EXISTS     = new ResultCode(500216, "手机号已注册!");
    public static ResultCode ORDER_NOT_CANCELLABLE = new ResultCode(500217, "当前订单不可取消!");
    public static ResultCode ORDER_STATE_INVALID = new ResultCode(500221, "订单状态不允许执行此操作!");
    public static ResultCode ADDRESS_NOT_EXIST = new ResultCode(500218, "收货地址不存在!");
    public static ResultCode ADDRESS_REQUIRED  = new ResultCode(500219, "请先设置收货地址!");
    public static ResultCode ADMIN_AUTH_FAILED = new ResultCode(500220, "管理员认证失败!");
    public static ResultCode CSRF_ERROR       = new ResultCode(500222, "请求令牌无效!");
    public static ResultCode GOODS_NOT_EXIST   = new ResultCode(500510, "商品不存在!");
    public static ResultCode GOODS_IN_USE      = new ResultCode(500511, "商品已有订单，不能删除!");
    public static ResultCode GOODS_TIME_INVALID = new ResultCode(500512, "秒杀时间范围无效!");
    public static ResultCode GOODS_PRICE_INVALID = new ResultCode(500513, "秒杀价不能高于商品原价!");
    public static ResultCode GOODS_STOCK_INVALID = new ResultCode(500514, "补货数量必须大于0!");
    public static ResultCode GOODS_STOCK_EDIT_FORBIDDEN = new ResultCode(500515, "已有商品不能直接覆盖库存，请使用补货操作!");

    // ----------------------------- 订单 -----------------------------
    public static ResultCode ORDER_NOT_EXIST   = new ResultCode(500410, "订单不存在!");
    public static ResultCode ORDER_FORBIDDEN   = new ResultCode(500411, "无权访问该订单!");
    public static ResultCode ORDER_CLOSED      = new ResultCode(500412, "订单已关闭，无法支付!");
    public static ResultCode PAYMENT_FAILED    = new ResultCode(500413, "支付处理失败!");

    // ----------------------------- 秒杀 -----------------------------
    /** Redis 库存预扣后为负，或 DB 扣减失败，商品已售罄 */
    public static ResultCode SECKILL_OVER_ERROR = new ResultCode(500500, "商品秒杀完毕，库存不足!");
    /** seckill_order 唯一索引冲突，同一用户不能重复下单 */
    public static ResultCode REPEAT_SECKILL     = new ResultCode(500500, "不能重复秒杀!");
    /** 秒杀活动尚未开始 */
    public static ResultCode SECKILL_NOT_STARTED = new ResultCode(500501, "秒杀活动尚未开始!");
    /** 秒杀活动已经结束 */
    public static ResultCode SECKILL_ENDED       = new ResultCode(500502, "秒杀活动已经结束!");

    public ResultCode(int code, String msg) {
        this.code = code;
        this.msg  = msg;
    }

    public int    getCode()          { return code; }
    public void   setCode(int code)  { this.code = code; }
    public String getMsg()           { return msg; }
    public void   setMsg(String msg) { this.msg = msg; }

    /**
     * 填充 msg 中的占位符，返回新的 ResultCode 实例（原对象不变）。
     * 例：{@code BIND_ERROR.fillArgs("mobile")} → msg = "参数校验异常:mobile"
     *
     * @param args 可变参数，按顺序填充 msg 中的 %s 占位符
     */
    public ResultCode fillArgs(Object... args) {
        int    code    = this.code;
        String message = String.format(this.msg, args);
        return new ResultCode(code, message);
    }

    @Override
    public String toString() {
        return "Result [code=" + code + ", msg=" + msg + "]";
    }
}

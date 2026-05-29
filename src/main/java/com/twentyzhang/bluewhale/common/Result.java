package com.twentyzhang.bluewhale.common;

import lombok.Getter;
import lombok.ToString;

/**
 * 统一 API 响应封装。
 * 通过静态工厂方法创建实例，禁止外部直接 new。
 */
@Getter
@ToString
public class Result<T> {

    public static final int CODE_SUCCESS      = 200;
    public static final int CODE_BAD_REQUEST  = 400;
    public static final int CODE_UNAUTHORIZED = 401;
    public static final int CODE_FORBIDDEN    = 403;
    public static final int CODE_NOT_FOUND    = 404;
    public static final int CODE_SERVER_ERROR = 500;

    private final int code;
    private final String message;
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 操作成功，无返回数据 */
    public static <T> Result<T> success() {
        return new Result<>(CODE_SUCCESS, "success", null);
    }

    /** 操作成功，携带返回数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(CODE_SUCCESS, "success", data);
    }

    /** 操作失败，默认 500 错误码 */
    public static <T> Result<T> fail(String message) {
        return new Result<>(CODE_SERVER_ERROR, message, null);
    }

    /** 操作失败，自定义错误码 */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    // ---------- 语义化快捷方法 ----------

    /** 400 请求参数错误 */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(CODE_BAD_REQUEST, message, null);
    }

    /** 401 未登录 / Token 无效 */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(CODE_UNAUTHORIZED, message, null);
    }

    /** 403 无权限 */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(CODE_FORBIDDEN, message, null);
    }

    /** 404 资源不存在 */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(CODE_NOT_FOUND, message, null);
    }
}

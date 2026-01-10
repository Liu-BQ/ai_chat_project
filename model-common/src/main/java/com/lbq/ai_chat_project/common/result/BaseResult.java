package com.lbq.ai_chat_project.common.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一 API 响应封装类。
 * <p>
 * 成功：{ "code": 200, "message": "OK", "data": {...} }
 * 失败：{ "code": 400, "message": "Invalid input", "data": null }
 *
 * @param <T> 响应数据类型
 */
@Data
@Accessors(chain = true)
public class BaseResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 响应码：200=成功，非200=失败 */
    private String code;

    /** 响应消息 */
    private String message;

    /** 响应数据 */
    private T data;

    // ===== 构造方法 =====

    private BaseResult(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ===== 静态工厂方法 =====

    public static <T> BaseResult<T> success(T data) {
        return new BaseResult<>("200", "OK", data);
    }

    public static <T> BaseResult<T> success() {
        return success(null);
    }

    public static <T> BaseResult<T> failure(String code, String message) {
        return new BaseResult<>(code, message, null);
    }

    public static <T> BaseResult<T> failure(String message) {
        return failure("400", message);
    }

    public static <T> BaseResult<T> error(String message) {
        return failure("500", message);
    }
}
package com.lbq.ai_chat_project.common.exception;

import lombok.Getter;

/**
 * 业务异常基类，所有自定义业务异常应继承此类。
 * <p>
 * 特点：
 * - 包含错误码（errorCode）
 * - 支持国际化消息（预留）
 * - 可携带额外上下文信息（如 field, value）
 * 使用示例： throw new BaseException("USER_NOT_FOUND", "User %s does not exist", userId);
 */
public class BaseException extends RuntimeException {

    @Getter
    private final String errorCode;
    private final Object[] args; // 用于消息模板参数

    public BaseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = new Object[0];
    }

    public BaseException(String errorCode, String message, Object... args) {
        super(String.format(message, args));
        this.errorCode = errorCode;
        this.args = args != null ? args.clone() : new Object[0];
    }

    public BaseException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = new Object[0];
    }

    public Object[] getArgs() {
        return args.clone();
    }
}
package com.timo.words.common;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    UNAUTHORIZED(401, "未登录或token已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    BAD_REQUEST(400, "请求参数错误"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 业务错误码 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    WORD_NOT_FOUND(1004, "单词不存在"),
    STUDY_SESSION_ERROR(1005, "学习会话异常"),

    // 管理员错误码 3xxx
    USER_BANNED(3001, "账号已被封禁，请联系管理员"),
    ADMIN_REQUIRED(3002, "需要管理员权限"),
    SUPER_ADMIN_REQUIRED(3003, "需要超级管理员权限"),
    ADMIN_SECRET_INVALID(3004, "管理员密钥错误"),

    // Agent 错误码 2xxx
    AI_SERVICE_UNAVAILABLE(2001, "AI服务暂不可用，已切换至经典模式"),
    AI_RESPONSE_ERROR(2002, "AI响应解析失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

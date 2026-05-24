package com.example.assistant.application.chat;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "对话不存在"),
    SKILL_NOT_FOUND(HttpStatus.BAD_REQUEST, "SKILL_NOT_FOUND", "Skill 不存在或不可用"),
    MODEL_NOT_FOUND(HttpStatus.BAD_REQUEST, "MODEL_NOT_FOUND", "模型不存在或不可用"),
    MCP_TOOL_NOT_FOUND(HttpStatus.BAD_REQUEST, "MCP_TOOL_NOT_FOUND", "MCP 工具不存在或不可用"),
    SKILL_EXTRACTION_EMPTY(HttpStatus.BAD_REQUEST, "SKILL_EXTRACTION_EMPTY", "当前对话内容不足，无法抽取 Skill"),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "FILE_TYPE_NOT_ALLOWED", "不支持的文件类型"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "文件大小超过限制"),
    INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "INVALID_FILE_NAME", "文件名非法"),
    STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED", "文件存储失败"),
    CHAT_STREAM_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT_STREAM_FAILED", "对话流生成失败");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}

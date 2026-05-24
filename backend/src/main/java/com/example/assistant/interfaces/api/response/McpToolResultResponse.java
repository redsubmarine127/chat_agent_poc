package com.example.assistant.interfaces.api.response;

import com.example.assistant.application.mcp.McpToolResult;

import java.util.Map;

public record McpToolResultResponse(
        String toolName,
        boolean success,
        String content,
        Map<String, Object> metadata
) {

    public static McpToolResultResponse from(McpToolResult result) {
        return new McpToolResultResponse(result.toolName(), result.success(), result.content(), result.metadata());
    }
}

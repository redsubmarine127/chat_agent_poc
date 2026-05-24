package com.example.assistant.application.mcp;

import java.util.Map;
import java.util.Objects;

public record McpToolResult(
        String toolName,
        boolean success,
        String content,
        Map<String, Object> metadata
) {

    public McpToolResult {
        toolName = Objects.requireNonNullElse(toolName, "");
        content = Objects.requireNonNullElse(content, "");
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
    }
}

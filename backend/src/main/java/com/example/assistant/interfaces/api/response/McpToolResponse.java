package com.example.assistant.interfaces.api.response;

import com.example.assistant.application.mcp.McpToolDescriptor;

public record McpToolResponse(
        String name,
        String description,
        boolean enabled
) {

    public static McpToolResponse from(McpToolDescriptor descriptor) {
        return new McpToolResponse(descriptor.name(), descriptor.description(), descriptor.enabled());
    }
}

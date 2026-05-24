package com.example.assistant.application.mcp;

import java.util.Objects;

public record McpToolDescriptor(
        String name,
        String description,
        boolean enabled
) {

    public McpToolDescriptor {
        name = Objects.requireNonNullElse(name, "");
        description = Objects.requireNonNullElse(description, "");
    }
}

package com.example.assistant.interfaces.api.request;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record McpInvokeRequest(
        @NotNull Map<String, Object> arguments
) {
}

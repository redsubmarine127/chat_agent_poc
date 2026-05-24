package com.example.assistant.application.observability;

import java.util.Objects;
import java.util.UUID;

public record ChatTraceContext(
        UUID conversationId,
        UUID messageId,
        String runtime,
        String modelId,
        String skillId,
        String input
) {

    public ChatTraceContext {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        runtime = Objects.requireNonNullElse(runtime, "");
        modelId = Objects.requireNonNullElse(modelId, "");
        skillId = Objects.requireNonNullElse(skillId, "");
        input = Objects.requireNonNullElse(input, "");
    }
}

package com.example.assistant.application.chat;

import java.util.UUID;

public record ChatStreamEvent(
        String type,
        UUID messageId,
        String content
) {

    public static ChatStreamEvent started(UUID messageId) {
        return new ChatStreamEvent("started", messageId, "");
    }

    public static ChatStreamEvent delta(UUID messageId, String content) {
        return new ChatStreamEvent("delta", messageId, content);
    }

    public static ChatStreamEvent reasoning(UUID messageId, String content) {
        return new ChatStreamEvent("reasoning", messageId, content);
    }

    public static ChatStreamEvent completed(UUID messageId) {
        return new ChatStreamEvent("completed", messageId, "");
    }

    public static ChatStreamEvent failed(UUID messageId, String reason) {
        return new ChatStreamEvent("failed", messageId, reason);
    }
}

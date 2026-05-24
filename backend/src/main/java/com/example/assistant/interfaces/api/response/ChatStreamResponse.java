package com.example.assistant.interfaces.api.response;

import com.example.assistant.application.chat.ChatStreamEvent;

import java.util.UUID;

public record ChatStreamResponse(
        String type,
        UUID messageId,
        String content
) {

    public static ChatStreamResponse from(ChatStreamEvent event) {
        return new ChatStreamResponse(event.type(), event.messageId(), event.content());
    }
}

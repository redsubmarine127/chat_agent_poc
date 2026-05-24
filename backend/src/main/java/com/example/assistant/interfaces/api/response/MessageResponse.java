package com.example.assistant.interfaces.api.response;

import com.example.assistant.domain.chat.ChatMessage;
import com.example.assistant.domain.chat.MessageRole;
import com.example.assistant.domain.chat.MessageStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        MessageRole role,
        String content,
        String skillId,
        List<UUID> attachmentIds,
        MessageStatus status,
        Instant createdAt
) {

    public static MessageResponse from(ChatMessage message) {
        return new MessageResponse(
                message.id(),
                message.conversationId(),
                message.role(),
                message.content(),
                message.skillId(),
                message.attachmentIds(),
                message.status(),
                message.createdAt()
        );
    }
}

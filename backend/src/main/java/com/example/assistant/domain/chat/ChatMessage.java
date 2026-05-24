package com.example.assistant.domain.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessage(
        UUID id,
        UUID conversationId,
        MessageRole role,
        String content,
        String skillId,
        List<UUID> attachmentIds,
        MessageStatus status,
        Instant createdAt
) {

    public static ChatMessage user(UUID conversationId, String content, String skillId, List<UUID> attachmentIds) {
        return new ChatMessage(
                UUID.randomUUID(),
                conversationId,
                MessageRole.USER,
                content,
                skillId,
                List.copyOf(attachmentIds),
                MessageStatus.COMPLETED,
                Instant.now()
        );
    }

    public static ChatMessage assistant(UUID conversationId, String content, String skillId) {
        return new ChatMessage(
                UUID.randomUUID(),
                conversationId,
                MessageRole.ASSISTANT,
                content,
                skillId,
                List.of(),
                MessageStatus.COMPLETED,
                Instant.now()
        );
    }
}

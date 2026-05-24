package com.example.assistant.domain.chat;

import java.time.Instant;
import java.util.UUID;

public record Conversation(
        UUID id,
        String title,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {

    public static Conversation create(String title) {
        Instant now = Instant.now();
        return new Conversation(UUID.randomUUID(), title, false, now, now);
    }

    public Conversation markDeleted() {
        return new Conversation(id, title, true, createdAt, Instant.now());
    }
}

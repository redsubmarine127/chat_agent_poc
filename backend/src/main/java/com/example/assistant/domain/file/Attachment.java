package com.example.assistant.domain.file;

import java.time.Instant;
import java.util.UUID;

public record Attachment(
        UUID id,
        String originalFilename,
        String contentType,
        String storageKey,
        long sizeInBytes,
        Instant createdAt
) {
}

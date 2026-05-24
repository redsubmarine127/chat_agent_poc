package com.example.assistant.interfaces.api.response;

import com.example.assistant.domain.file.Attachment;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        String originalFilename,
        String contentType,
        long sizeInBytes,
        Instant createdAt
) {

    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.id(),
                attachment.originalFilename(),
                attachment.contentType(),
                attachment.sizeInBytes(),
                attachment.createdAt()
        );
    }
}

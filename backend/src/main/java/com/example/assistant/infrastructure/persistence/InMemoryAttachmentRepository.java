package com.example.assistant.infrastructure.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Primary
@Repository
@ConditionalOnProperty(prefix = "assistant.persistence", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryAttachmentRepository
        extends InMemoryReactiveCrudRepository<AttachmentEntity, UUID>
        implements AttachmentRepository {
}

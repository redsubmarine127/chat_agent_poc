package com.example.assistant.infrastructure.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface AttachmentRepository extends ReactiveCrudRepository<AttachmentEntity, UUID> {
}

package com.example.assistant.infrastructure.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConversationRepository extends ReactiveCrudRepository<ConversationEntity, UUID> {

    Flux<ConversationEntity> findByDeletedFalseOrderByUpdatedAtDesc();

    Mono<ConversationEntity> findByIdAndDeletedFalse(UUID id);
}

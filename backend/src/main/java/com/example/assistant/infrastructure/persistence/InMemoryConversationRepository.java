package com.example.assistant.infrastructure.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.UUID;

@Primary
@Repository
@ConditionalOnProperty(prefix = "assistant.persistence", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryConversationRepository
        extends InMemoryReactiveCrudRepository<ConversationEntity, UUID>
        implements ConversationRepository {

    @Override
    public Flux<ConversationEntity> findByDeletedFalseOrderByUpdatedAtDesc() {
        return values()
                .filter(entity -> !entity.isDeleted())
                .sort(Comparator.comparing(
                        ConversationEntity::getUpdatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed());
    }

    @Override
    public Mono<ConversationEntity> findByIdAndDeletedFalse(UUID id) {
        return findById(id).filter(entity -> !entity.isDeleted());
    }
}

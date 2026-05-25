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
public class InMemoryChatMessageRepository
        extends InMemoryReactiveCrudRepository<ChatMessageEntity, UUID>
        implements ChatMessageRepository {

    @Override
    public Flux<ChatMessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId) {
        return values()
                .filter(entity -> conversationId.equals(entity.getConversationId()))
                .sort(Comparator.comparing(
                        ChatMessageEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ));
    }

    @Override
    public Mono<Integer> deleteByConversationId(UUID conversationId) {
        return removeMatching(entity -> conversationId.equals(entity.getConversationId()));
    }
}

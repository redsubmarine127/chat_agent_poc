package com.example.assistant.infrastructure.persistence;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ChatMessageRepository extends ReactiveCrudRepository<ChatMessageEntity, UUID> {

    Flux<ChatMessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    @Modifying
    @Query("DELETE FROM chat_messages WHERE conversation_id = :conversationId")
    Mono<Integer> deleteByConversationId(UUID conversationId);
}

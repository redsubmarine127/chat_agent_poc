package com.example.assistant.application.chat;

import com.example.assistant.domain.chat.ChatMessage;
import com.example.assistant.domain.chat.Conversation;
import com.example.assistant.infrastructure.persistence.ChatMessageRepository;
import com.example.assistant.infrastructure.persistence.ConversationEntity;
import com.example.assistant.infrastructure.persistence.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TransactionalOperator transactionalOperator;

    public ConversationService(
            ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository,
            TransactionalOperator transactionalOperator
    ) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.transactionalOperator = transactionalOperator;
    }

    public Flux<Conversation> listConversations() {
        return conversationRepository.findByDeletedFalseOrderByUpdatedAtDesc()
                .map(ConversationEntity::toDomain);
    }

    public Mono<Conversation> createConversation(String title) {
        String normalizedTitle = title == null || title.isBlank() ? "新的对话" : title.strip();
        return conversationRepository.save(ConversationEntity.newFromDomain(Conversation.create(normalizedTitle)))
                .map(ConversationEntity::toDomain)
                .as(transactionalOperator::transactional);
    }

    public Mono<Void> deleteConversation(UUID conversationId) {
        return findConversation(conversationId)
                .map(Conversation::markDeleted)
                .map(ConversationEntity::existingFromDomain)
                .flatMap(conversationRepository::save)
                .then()
                .as(transactionalOperator::transactional);
    }

    public Flux<ChatMessage> listMessages(UUID conversationId) {
        return findConversation(conversationId)
                .thenMany(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .map(entity -> entity.toDomain());
    }

    public Mono<Void> clearMessages(UUID conversationId) {
        return findConversation(conversationId)
                .then(chatMessageRepository.deleteByConversationId(conversationId))
                .then()
                .as(transactionalOperator::transactional);
    }

    public Mono<Conversation> findConversation(UUID conversationId) {
        return conversationRepository.findByIdAndDeletedFalse(conversationId)
                .map(ConversationEntity::toDomain)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND)));
    }
}

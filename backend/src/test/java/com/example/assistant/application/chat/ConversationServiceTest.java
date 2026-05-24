package com.example.assistant.application.chat;

import com.example.assistant.domain.chat.Conversation;
import com.example.assistant.infrastructure.persistence.ChatMessageRepository;
import com.example.assistant.infrastructure.persistence.ConversationEntity;
import com.example.assistant.infrastructure.persistence.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private TransactionalOperator transactionalOperator;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(conversationRepository, chatMessageRepository, transactionalOperator);
        lenient().when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createConversationShouldReturnSavedConversation() {
        when(conversationRepository.save(any(ConversationEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(conversationService.createConversation("生产问题排查"))
                .expectNextMatches(conversation -> "生产问题排查".equals(conversation.title()) && !conversation.deleted())
                .verifyComplete();
    }

    @Test
    void deleteMissingConversationShouldReturnBusinessException() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findByIdAndDeletedFalse(conversationId)).thenReturn(Mono.empty());

        StepVerifier.create(conversationService.deleteConversation(conversationId))
                .expectErrorMatches(throwable -> throwable instanceof BusinessException businessException
                        && businessException.errorCode() == ErrorCode.CONVERSATION_NOT_FOUND)
                .verify();
    }

    @Test
    void listMessagesShouldFailWhenConversationMissing() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findByIdAndDeletedFalse(conversationId)).thenReturn(Mono.empty());

        StepVerifier.create(conversationService.listMessages(conversationId))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void clearMessagesShouldDeleteMessagesWhenConversationExists() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findByIdAndDeletedFalse(conversationId))
                .thenReturn(Mono.just(ConversationEntity.newFromDomain(Conversation.create("上下文清理"))));
        when(chatMessageRepository.deleteByConversationId(conversationId)).thenReturn(Mono.just(3));

        StepVerifier.create(conversationService.clearMessages(conversationId))
                .verifyComplete();

        verify(chatMessageRepository).deleteByConversationId(conversationId);
    }
}

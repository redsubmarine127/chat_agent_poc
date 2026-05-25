package com.example.assistant.infrastructure.persistence;

import com.example.assistant.application.chat.ConversationService;
import com.example.assistant.domain.chat.ChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.util.UUID;

@SpringBootTest(properties = {
        "assistant.persistence.mode=memory",
        "spring.r2dbc.url=r2dbc:postgresql://127.0.0.1:65432/missing",
        "spring.flyway.url=jdbc:opengauss://127.0.0.1:65432/missing"
})
class MemoryPersistenceIntegrationTest {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Test
    void memoryModeShouldRunConversationFlowWithoutDatabase() {
        UUID conversationId = conversationService.createConversation("内存测试")
                .map(conversation -> conversation.id())
                .block();

        StepVerifier.create(conversationService.listConversations())
                .expectNextMatches(conversation -> conversation.id().equals(conversationId)
                        && "内存测试".equals(conversation.title()))
                .verifyComplete();

        ChatMessage message = ChatMessage.user(conversationId, "你好", "general", java.util.List.of());
        StepVerifier.create(chatMessageRepository.save(ChatMessageEntity.newFromDomain(message))
                        .thenMany(conversationService.listMessages(conversationId)))
                .expectNextMatches(savedMessage -> savedMessage.id().equals(message.id())
                        && "你好".equals(savedMessage.content()))
                .verifyComplete();

        StepVerifier.create(conversationService.clearMessages(conversationId)
                        .thenMany(conversationService.listMessages(conversationId)))
                .verifyComplete();
    }
}

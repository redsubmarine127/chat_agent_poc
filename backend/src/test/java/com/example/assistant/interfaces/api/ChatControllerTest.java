package com.example.assistant.interfaces.api;

import com.example.assistant.application.chat.ChatStreamEvent;
import com.example.assistant.application.chat.ChatStreamService;
import com.example.assistant.interfaces.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    @Test
    void streamShouldReturnServerSentEvents() {
        ChatStreamService chatStreamService = mock(ChatStreamService.class);
        ChatController chatController = new ChatController(chatStreamService);
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(chatStreamService.stream(eq(conversationId), eq("你好"), eq("general"), eq("local-fallback"), any(List.class)))
                .thenReturn(Flux.just(ChatStreamEvent.started(messageId), ChatStreamEvent.completed(messageId)));

        WebTestClient.bindToController(chatController)
                .controllerAdvice(new GlobalExceptionHandler())
                .build()
                .post()
                .uri("/api/conversations/{conversationId}/messages/stream", conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "content": "你好",
                          "skillId": "general",
                          "modelId": "local-fallback",
                          "attachmentIds": []
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    void streamShouldRejectBlankContent() {
        ChatStreamService chatStreamService = mock(ChatStreamService.class);
        ChatController chatController = new ChatController(chatStreamService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        WebTestClient.bindToController(chatController)
                .controllerAdvice(new GlobalExceptionHandler())
                .validator(validator)
                .build()
                .post()
                .uri("/api/conversations/{conversationId}/messages/stream", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "content": " ",
                          "skillId": "general",
                          "modelId": "local-fallback",
                          "attachmentIds": []
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }
}

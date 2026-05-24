package com.example.assistant.interfaces.api;

import com.example.assistant.application.chat.ConversationService;
import com.example.assistant.interfaces.api.request.CreateConversationRequest;
import com.example.assistant.interfaces.api.response.ConversationResponse;
import com.example.assistant.interfaces.api.response.MessageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public Flux<ConversationResponse> listConversations() {
        return conversationService.listConversations().map(ConversationResponse::from);
    }

    @PostMapping
    public Mono<ConversationResponse> createConversation(@Valid @RequestBody CreateConversationRequest request) {
        return conversationService.createConversation(request.title()).map(ConversationResponse::from);
    }

    @DeleteMapping("/{conversationId}")
    public Mono<Void> deleteConversation(@PathVariable UUID conversationId) {
        return conversationService.deleteConversation(conversationId);
    }

    @GetMapping("/{conversationId}/messages")
    public Flux<MessageResponse> listMessages(@PathVariable UUID conversationId) {
        return conversationService.listMessages(conversationId).map(MessageResponse::from);
    }

    @DeleteMapping("/{conversationId}/messages")
    public Mono<Void> clearMessages(@PathVariable UUID conversationId) {
        return conversationService.clearMessages(conversationId);
    }
}

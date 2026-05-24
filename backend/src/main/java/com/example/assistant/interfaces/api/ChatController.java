package com.example.assistant.interfaces.api;

import com.example.assistant.application.chat.ChatStreamService;
import com.example.assistant.interfaces.api.request.StreamChatRequest;
import com.example.assistant.interfaces.api.response.ChatStreamResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ChatController {

    private final ChatStreamService chatStreamService;

    public ChatController(ChatStreamService chatStreamService) {
        this.chatStreamService = chatStreamService;
    }

    @PostMapping(value = "/{conversationId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamResponse>> stream(
            @PathVariable UUID conversationId,
            @Valid @RequestBody StreamChatRequest request
    ) {
        return chatStreamService.stream(conversationId, request.content(), request.skillId(), request.modelId(), request.attachmentIds())
                .map(event -> ServerSentEvent.<ChatStreamResponse>builder(ChatStreamResponse.from(event))
                        .event(event.type())
                        .id(event.messageId().toString())
                        .build());
    }
}

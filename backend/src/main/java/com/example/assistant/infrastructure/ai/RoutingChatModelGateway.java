package com.example.assistant.infrastructure.ai;

import com.example.assistant.application.chat.ChatModelGateway;
import reactor.core.publisher.Flux;

public class RoutingChatModelGateway implements ChatModelGateway {

    private final ChatModelGateway localFallbackGateway;
    private final ChatModelGateway openAiCompatibleGateway;

    public RoutingChatModelGateway(ChatModelGateway localFallbackGateway, ChatModelGateway openAiCompatibleGateway) {
        this.localFallbackGateway = localFallbackGateway;
        this.openAiCompatibleGateway = openAiCompatibleGateway;
    }

    @Override
    public Flux<ChatModelChunk> stream(ChatPrompt prompt) {
        if (prompt.model().localProvider()) {
            return localFallbackGateway.stream(prompt);
        }
        return openAiCompatibleGateway.stream(prompt);
    }
}

package com.example.assistant.infrastructure.ai;

import com.example.assistant.application.chat.ChatModelGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

public class RoutingChatModelGateway implements ChatModelGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoutingChatModelGateway.class);

    private final ChatModelGateway localFallbackGateway;
    private final ChatModelGateway openAiCompatibleGateway;

    public RoutingChatModelGateway(ChatModelGateway localFallbackGateway, ChatModelGateway openAiCompatibleGateway) {
        this.localFallbackGateway = localFallbackGateway;
        this.openAiCompatibleGateway = openAiCompatibleGateway;
    }

    @Override
    public Flux<ChatModelChunk> stream(ChatPrompt prompt) {
        LOGGER.info(
                "llm route selected, modelId={}, provider={}, localProvider={}, hasApiKey={}, historyCount={}, ragContextCount={}, mcpToolCount={}",
                prompt.model().id(),
                prompt.model().provider(),
                prompt.model().localProvider(),
                prompt.model().apiKey() != null && !prompt.model().apiKey().isBlank(),
                prompt.history().size(),
                prompt.ragContexts().size(),
                prompt.mcpTools().size()
        );
        if (prompt.model().localProvider()) {
            return localFallbackGateway.stream(prompt);
        }
        return openAiCompatibleGateway.stream(prompt);
    }
}

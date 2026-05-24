package com.example.assistant.application.chat;

import com.example.assistant.application.mcp.McpToolDescriptor;
import com.example.assistant.application.rag.RagContext;
import com.example.assistant.domain.chat.ChatMessage;
import com.example.assistant.domain.model.AiModel;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

public interface ChatModelGateway {

    Flux<ChatModelChunk> stream(ChatPrompt prompt);

    record ChatModelChunk(
            ChunkType type,
            String content
    ) {
        public ChatModelChunk {
            Objects.requireNonNull(type, "chunk type must not be null");
            content = content == null ? "" : content;
        }

        public static ChatModelChunk reasoning(String content) {
            return new ChatModelChunk(ChunkType.REASONING, content);
        }

        public static ChatModelChunk answer(String content) {
            return new ChatModelChunk(ChunkType.ANSWER, content);
        }
    }

    enum ChunkType {
        REASONING,
        ANSWER
    }

    record ChatPrompt(
            String userMessage,
            String skillInstruction,
            AiModel model,
            List<ChatMessage> history,
            List<RagContext> ragContexts,
            List<McpToolDescriptor> mcpTools
    ) {
        public ChatPrompt {
            userMessage = Objects.requireNonNullElse(userMessage, "");
            skillInstruction = Objects.requireNonNullElse(skillInstruction, "");
            model = Objects.requireNonNull(model, "model must not be null");
            history = List.copyOf(Objects.requireNonNullElse(history, List.of()));
            ragContexts = List.copyOf(Objects.requireNonNullElse(ragContexts, List.of()));
            mcpTools = List.copyOf(Objects.requireNonNullElse(mcpTools, List.of()));
        }
    }
}

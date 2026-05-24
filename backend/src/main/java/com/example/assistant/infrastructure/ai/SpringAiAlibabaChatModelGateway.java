package com.example.assistant.infrastructure.ai;

import com.example.assistant.application.chat.ChatModelGateway;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

public class SpringAiAlibabaChatModelGateway implements ChatModelGateway {

    private final ChatClient chatClient;

    public SpringAiAlibabaChatModelGateway(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Flux<ChatModelChunk> stream(ChatPrompt prompt) {
        String systemPrompt = """
                你是一个生产级智能对话助手。
                当前 Skill 指令：%s
                请回答准确、结构清晰，并在不确定时明确说明。
                """.formatted(prompt.skillInstruction());

        return chatClient.prompt()
                .system(systemPrompt)
                .user(prompt.userMessage())
                .stream()
                .content()
                .map(ChatModelChunk::answer);
    }
}

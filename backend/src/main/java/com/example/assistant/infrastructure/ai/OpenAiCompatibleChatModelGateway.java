package com.example.assistant.infrastructure.ai;

import com.example.assistant.application.chat.ChatModelGateway;
import com.example.assistant.application.mcp.McpToolDescriptor;
import com.example.assistant.application.rag.RagContext;
import com.example.assistant.domain.chat.MessageRole;
import com.example.assistant.domain.model.AiModel;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class OpenAiCompatibleChatModelGateway implements ChatModelGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiCompatibleChatModelGateway.class);

    @Override
    public Flux<ChatModelChunk> stream(ChatPrompt prompt) {
        AiModel model = prompt.model();
        if (!StringUtils.hasText(model.apiKey())) {
            return Flux.error(new IllegalStateException("模型 %s 未配置 API Key".formatted(model.name())));
        }
        if (!StringUtils.hasText(model.baseUrl())) {
            return Flux.error(new IllegalStateException("模型 %s 未配置 Base URL".formatted(model.name())));
        }

        LOGGER.info(
                "llm remote stream starting, modelId={}, provider={}, baseUrl={}, modelName={}, historyCount={}, ragContextCount={}, mcpToolCount={}",
                model.id(),
                model.provider(),
                normalizeBaseUrl(model.baseUrl()),
                model.modelName(),
                prompt.history().size(),
                prompt.ragContexts().size(),
                prompt.mcpTools().size()
        );
        return Flux.defer(() -> Flux.using(
                () -> createClientStream(prompt),
                this::readResponseStream,
                ClientStream::close
        )).subscribeOn(Schedulers.boundedElastic());
    }

    private ClientStream createClientStream(ChatPrompt prompt) {
        AiModel model = prompt.model();
        LOGGER.info("llm remote client stream create started, modelId={}, baseUrl={}", model.id(), normalizeBaseUrl(model.baseUrl()));
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(model.apiKey())
                .baseUrl(normalizeBaseUrl(model.baseUrl()))
                .build();
        StreamResponse<ChatCompletionChunk> response = client.chat()
                .completions()
                .createStreaming(buildRequest(prompt));
        LOGGER.info("llm remote client stream create completed, modelId={}", model.id());
        return new ClientStream(client, response);
    }

    private ChatCompletionCreateParams buildRequest(ChatPrompt prompt) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(prompt.model().modelName())
                .streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build())
                .addSystemMessage(buildSystemPrompt(prompt));
        prompt.history().stream()
                .filter(message -> StringUtils.hasText(message.content()))
                .forEach(message -> addHistoryMessage(builder, message.role(), message.content()));
        return builder.addUserMessage(prompt.userMessage()).build();
    }

    private String buildSystemPrompt(ChatPrompt prompt) {
        StringBuilder builder = new StringBuilder("""
                你是一个生产级智能对话助手。
                当前 Skill 指令：%s
                请回答准确、结构清晰，并在不确定时明确说明。
                """.formatted(prompt.skillInstruction()));
        if (!prompt.ragContexts().isEmpty()) {
            builder.append("\n可参考的 RAG 上下文如下。若上下文与问题无关，请说明并基于通用知识回答：\n");
            for (RagContext context : prompt.ragContexts()) {
                builder.append("- 来源：")
                        .append(context.title())
                        .append("，相关度：")
                        .append(String.format("%.2f", context.score()))
                        .append("\n  内容：")
                        .append(context.content())
                        .append('\n');
            }
        }
        if (!prompt.mcpTools().isEmpty()) {
            builder.append("\n已注册 MCP 工具清单。当前对话只暴露工具能力说明，真实工具调用由后续执行器扩展：\n");
            for (McpToolDescriptor tool : prompt.mcpTools()) {
                builder.append("- ")
                        .append(tool.name())
                        .append("：")
                        .append(tool.description())
                        .append('\n');
            }
        }
        return builder.toString();
    }

    private void addHistoryMessage(ChatCompletionCreateParams.Builder builder, MessageRole role, String content) {
        switch (role) {
            case USER -> builder.addUserMessage(content);
            case ASSISTANT -> builder.addAssistantMessage(content);
            case SYSTEM -> builder.addSystemMessage(content);
        }
    }

    private Flux<ChatModelChunk> readResponseStream(ClientStream clientStream) {
        AtomicInteger chunkCounter = new AtomicInteger();
        return Flux.fromStream(clientStream.response().stream())
                .flatMapIterable(chunk -> Optional.ofNullable(chunk.choices()).orElse(List.of()))
                .flatMapIterable(choice -> {
                    ChatCompletionChunk.Choice.Delta delta = choice.delta();
                    String reasoningContent = readAdditionalString(delta, "reasoning_content")
                            .or(() -> readAdditionalString(delta, "reasoningContent"))
                            .orElse("");
                    String answerContent = delta.content().orElse("");
                    return List.of(
                                    ChatModelChunk.reasoning(reasoningContent),
                                    ChatModelChunk.answer(answerContent)
                            )
                            .stream()
                            .filter(chunk -> StringUtils.hasText(chunk.content()))
                            .toList();
                })
                .doOnNext(ignored -> chunkCounter.incrementAndGet())
                .doOnComplete(() -> LOGGER.info("llm remote stream completed, chunkCount={}", chunkCounter.get()));
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private Optional<String> readAdditionalString(ChatCompletionChunk.Choice.Delta delta, String key) {
        JsonValue value = delta._additionalProperties().get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(value.convert(String.class)).filter(StringUtils::hasText);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private record ClientStream(OpenAIClient client, StreamResponse<ChatCompletionChunk> response) implements AutoCloseable {
        @Override
        public void close() {
            response.close();
            client.close();
            LOGGER.info("llm remote client stream closed");
        }
    }
}

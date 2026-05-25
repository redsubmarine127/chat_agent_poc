package com.example.assistant.application.chat;

import com.example.assistant.application.model.ModelService;
import com.example.assistant.application.mcp.McpToolService;
import com.example.assistant.application.observability.ChatObservability;
import com.example.assistant.application.observability.ChatTrace;
import com.example.assistant.application.observability.ChatTraceContext;
import com.example.assistant.application.rag.RagRetrievalService;
import com.example.assistant.application.skill.SkillService;
import com.example.assistant.domain.chat.ChatMessage;
import com.example.assistant.domain.chat.MessageRole;
import com.example.assistant.domain.chat.MessageStatus;
import com.example.assistant.infrastructure.persistence.ChatMessageEntity;
import com.example.assistant.infrastructure.persistence.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class ChatStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStreamService.class);
    private static final int AGENT_MAX_ATTEMPTS = 3;
    private static final Duration AGENT_RETRY_DELAY = Duration.ofMillis(250);

    private final ConversationService conversationService;
    private final SkillService skillService;
    private final ModelService modelService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatModelGateway chatModelGateway;
    private final RagRetrievalService ragRetrievalService;
    private final McpToolService mcpToolService;
    private final TransactionalOperator transactionalOperator;
    private final ChatObservability chatObservability;

    public ChatStreamService(
            ConversationService conversationService,
            SkillService skillService,
            ModelService modelService,
            ChatMessageRepository chatMessageRepository,
            ChatModelGateway chatModelGateway,
            RagRetrievalService ragRetrievalService,
            McpToolService mcpToolService,
            TransactionalOperator transactionalOperator,
            ChatObservability chatObservability
    ) {
        this.conversationService = conversationService;
        this.skillService = skillService;
        this.modelService = modelService;
        this.chatMessageRepository = chatMessageRepository;
        this.chatModelGateway = chatModelGateway;
        this.ragRetrievalService = ragRetrievalService;
        this.mcpToolService = mcpToolService;
        this.transactionalOperator = transactionalOperator;
        this.chatObservability = chatObservability;
    }

    public Flux<ChatStreamEvent> stream(UUID conversationId, String content, String skillId, String modelId, List<UUID> attachmentIds) {
        return Flux.defer(() -> doStream(conversationId, content, skillId, modelId, attachmentIds));
    }

    private Flux<ChatStreamEvent> doStream(UUID conversationId, String content, String skillId, String modelId, List<UUID> attachmentIds) {
        UUID assistantMessageId = UUID.randomUUID();
        String userContent = content == null ? "" : content.strip();
        List<UUID> safeAttachmentIds = attachmentIds == null
                ? List.of()
                : attachmentIds.stream().filter(Objects::nonNull).toList();
        Map<String, Object> requestContext = Map.of(
                "conversationId", conversationId,
                "assistantMessageId", assistantMessageId,
                "modelId", modelId,
                "skillId", skillId,
                "attachmentCount", safeAttachmentIds.size(),
                "contentLength", userContent.length()
        );
        StringBuffer assistantContentBuffer = new StringBuffer(1024);
        LOGGER.info(
                "agent stream started, conversationId={}, assistantMessageId={}, modelId={}, skillId={}, attachmentCount={}, contentLength={}",
                conversationId,
                assistantMessageId,
                modelId,
                skillId,
                safeAttachmentIds.size(),
                userContent.length()
        );
        ChatTrace trace = chatObservability.startChatTrace(new ChatTraceContext(
                conversationId,
                assistantMessageId,
                "java",
                modelId,
                skillId,
                userContent
        ));
        Mono<ChatModelGateway.ChatPrompt> promptMono = Mono.zip(
                        retryMonoStep(
                                "database.find_conversation",
                                "数据库确认对话",
                                () -> conversationService.findConversation(conversationId),
                                requestContext
                        ),
                        retryMonoStep(
                                "skill.load",
                                "Skill 加载",
                                () -> skillService.getEnabledSkill(skillId),
                                requestContext
                        ),
                        retryMonoStep(
                                "model.resolve",
                                "模型配置解析",
                                () -> modelService.getEnabledModel(modelId),
                                requestContext
                        ),
                        retryMonoStep(
                                "database.load_history",
                                "数据库加载历史消息",
                                () -> chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                                        .map(ChatMessageEntity::toDomain)
                                        .collectList(),
                                requestContext
                        ),
                        retryMonoStep(
                                "rag.retrieve",
                                "RAG 上下文检索",
                                () -> ragRetrievalService.retrieve(userContent, 5).collectList(),
                                requestContext
                        ),
                        retryMonoStep(
                                "mcp.list_tools",
                                "MCP 工具加载",
                                () -> mcpToolService.listTools().collectList(),
                                requestContext
                        )
                )
                .flatMap(tuple -> {
                    safeTraceEvent(trace, "context.loaded", Map.of(
                            "historyCount", tuple.getT4().size(),
                            "ragContextIds", tuple.getT5().stream().map(context -> context.sourceId()).toList(),
                            "mcpToolNames", tuple.getT6().stream().map(tool -> tool.name()).toList()
                    ));
                    ChatMessage userMessage = ChatMessage.user(conversationId, userContent, skillId, safeAttachmentIds);
                    return retryMonoStep(
                            "database.save_user_message",
                            "数据库保存用户消息",
                            () -> chatMessageRepository.save(ChatMessageEntity.newFromDomain(userMessage)),
                            requestContext
                    )
                            .thenReturn(new ChatModelGateway.ChatPrompt(
                                    userMessage.content(),
                                    tuple.getT2().description(),
                                    tuple.getT3(),
                                    tuple.getT4(),
                                    tuple.getT5(),
                                    tuple.getT6()
                            ));
                })
                .as(transactionalOperator::transactional);

        Mono<ChatStreamEvent> completedMono = Mono.defer(() ->
                retryMonoStep(
                        "database.save_assistant_message",
                        "数据库保存助手消息",
                        () -> saveAssistantMessage(
                                assistantMessageId,
                                conversationId,
                                assistantContentBuffer.toString(),
                                skillId,
                                MessageStatus.COMPLETED
                        ),
                        requestContext
                )
                    .thenReturn(ChatStreamEvent.completed(assistantMessageId))
                    .doOnSuccess(ignored -> safeTraceComplete(trace, assistantContentBuffer.toString()))
        ).as(transactionalOperator::transactional);

        return promptMono.flatMapMany(prompt -> {
                    Flux<ChatStreamEvent> deltaFlux = streamModelWithRetry(prompt, requestContext)
                            .map(chunk -> {
                                if (chunk.type() == ChatModelGateway.ChunkType.REASONING) {
                                    return ChatStreamEvent.reasoning(assistantMessageId, chunk.content());
                                }
                                assistantContentBuffer.append(chunk.content());
                                return ChatStreamEvent.delta(assistantMessageId, chunk.content());
                            });
                    return Flux.concat(deltaFlux, completedMono);
                })
                .startWith(ChatStreamEvent.started(assistantMessageId))
                .onErrorResume(throwable -> {
                    LOGGER.error(
                            "chat stream failed, conversationId={}, modelId={}, skillId={}, assistantMessageId={}, context={}",
                            conversationId,
                            modelId,
                            skillId,
                            assistantMessageId,
                            requestContext,
                            throwable
                    );
                    String failureMessage = failureMessage(throwable);
                    return retryMonoStep(
                            "database.save_failed_assistant_message",
                            "数据库保存失败消息",
                            () -> saveAssistantMessage(
                                    assistantMessageId,
                                    conversationId,
                                    failureMessage,
                                    skillId,
                                    MessageStatus.FAILED
                            ),
                            requestContext
                    )
                            .onErrorResume(saveError -> {
                                LOGGER.warn(
                                        "save failed assistant message failed, conversationId={}, assistantMessageId={}, context={}",
                                        conversationId,
                                        assistantMessageId,
                                        requestContext,
                                        saveError
                                );
                                return Mono.empty();
                            })
                            .doOnSuccess(ignored -> safeTraceFail(
                                    trace,
                                    assistantContentBuffer.toString(),
                                    failureMessage
                            ))
                            .thenReturn(ChatStreamEvent.failed(assistantMessageId, failureMessage));
                });
    }

    private Mono<ChatMessageEntity> saveAssistantMessage(
            UUID assistantMessageId,
            UUID conversationId,
            String content,
            String skillId,
            MessageStatus status
    ) {
        ChatMessage assistantMessage = new ChatMessage(
                assistantMessageId,
                conversationId,
                MessageRole.ASSISTANT,
                content,
                skillId,
                List.of(),
                status,
                Instant.now()
        );
        return chatMessageRepository.save(ChatMessageEntity.newFromDomain(assistantMessage));
    }

    private <T> Mono<T> retryMonoStep(
            String stepName,
            String displayName,
            Supplier<Mono<T>> operation,
            Map<String, Object> requestContext
    ) {
        AtomicInteger attemptCounter = new AtomicInteger();
        return Mono.defer(() -> {
                    int attempt = attemptCounter.incrementAndGet();
                    LOGGER.info(
                            "agent step started, step={}, attempt={}, maxAttempts={}, context={}",
                            stepName,
                            attempt,
                            AGENT_MAX_ATTEMPTS,
                            requestContext
                    );
                    return operation.get()
                            .doOnSuccess(ignored -> LOGGER.info(
                                    "agent step succeeded, step={}, attempt={}, context={}",
                                    stepName,
                                    attempt,
                                    requestContext
                            ))
                            .doOnError(error -> LOGGER.warn(
                                    "agent step failed, step={}, attempt={}, maxAttempts={}, error={}, context={}",
                                    stepName,
                                    attempt,
                                    AGENT_MAX_ATTEMPTS,
                                    error.toString(),
                                    requestContext,
                                    error
                            ));
                })
                .retryWhen(Retry.fixedDelay(AGENT_MAX_ATTEMPTS - 1, AGENT_RETRY_DELAY)
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                new AgentStepFailedException(displayName, stepName, retrySignal.failure())));
    }

    private Flux<ChatModelGateway.ChatModelChunk> streamModelWithRetry(
            ChatModelGateway.ChatPrompt prompt,
            Map<String, Object> requestContext
    ) {
        return streamModelAttempt(prompt, requestContext, 1);
    }

    private Flux<ChatModelGateway.ChatModelChunk> streamModelAttempt(
            ChatModelGateway.ChatPrompt prompt,
            Map<String, Object> requestContext,
            int attempt
    ) {
        AtomicBoolean emittedChunk = new AtomicBoolean(false);
        return Flux.defer(() -> {
            LOGGER.info(
                    "agent step started, step=llm.stream, attempt={}, maxAttempts={}, modelId={}, promptMessageCount={}, context={}",
                    attempt,
                    AGENT_MAX_ATTEMPTS,
                    prompt.model().id(),
                    prompt.history().size() + 2,
                    requestContext
            );
            return chatModelGateway.stream(prompt)
                    .doOnNext(ignored -> emittedChunk.set(true))
                    .doOnComplete(() -> LOGGER.info(
                            "agent step succeeded, step=llm.stream, attempt={}, modelId={}, context={}",
                            attempt,
                            prompt.model().id(),
                            requestContext
                    ))
                    .onErrorResume(error -> {
                        LOGGER.warn(
                                "agent step failed, step=llm.stream, attempt={}, maxAttempts={}, emittedChunk={}, modelId={}, error={}, context={}",
                                attempt,
                                AGENT_MAX_ATTEMPTS,
                                emittedChunk.get(),
                                prompt.model().id(),
                                error.toString(),
                                requestContext,
                                error
                        );
                        if (emittedChunk.get() || attempt >= AGENT_MAX_ATTEMPTS) {
                            return Flux.error(new AgentStepFailedException("模型调用", "llm.stream", error));
                        }
                        int nextAttempt = attempt + 1;
                        return Flux.concat(
                                Flux.just(ChatModelGateway.ChatModelChunk.reasoning("模型调用暂时失败，正在进行第 %s 次尝试。".formatted(nextAttempt))),
                                Mono.delay(AGENT_RETRY_DELAY.multipliedBy(attempt))
                                        .thenMany(streamModelAttempt(prompt, requestContext, nextAttempt))
                        );
                    });
        });
    }

    private String failureMessage(Throwable throwable) {
        if (throwable instanceof AgentStepFailedException exception) {
            return "%s 连续重试 %s 次仍未成功，已主动停止流程。请检查相关配置或稍后重试。"
                    .formatted(exception.displayName(), AGENT_MAX_ATTEMPTS);
        }
        if (throwable instanceof BusinessException exception) {
            return exception.errorCode().message();
        }
        return ErrorCode.CHAT_STREAM_FAILED.message();
    }

    private void safeTraceEvent(ChatTrace trace, String name, Map<String, Object> metadata) {
        try {
            trace.event(name, metadata);
        } catch (RuntimeException exception) {
            LOGGER.debug("chat trace event failed, name={}", name, exception);
        }
    }

    private void safeTraceComplete(ChatTrace trace, String output) {
        try {
            trace.complete(output);
        } catch (RuntimeException exception) {
            LOGGER.debug("chat trace complete failed", exception);
        }
    }

    private void safeTraceFail(ChatTrace trace, String output, String errorMessage) {
        try {
            trace.fail(output, errorMessage);
        } catch (RuntimeException exception) {
            LOGGER.debug("chat trace fail failed", exception);
        }
    }

    private static final class AgentStepFailedException extends RuntimeException {

        private final String displayName;

        private AgentStepFailedException(String displayName, String stepName, Throwable cause) {
            super("%s failed after retries, step=%s".formatted(displayName, stepName), cause);
            this.displayName = displayName;
        }

        private String displayName() {
            return displayName;
        }
    }
}

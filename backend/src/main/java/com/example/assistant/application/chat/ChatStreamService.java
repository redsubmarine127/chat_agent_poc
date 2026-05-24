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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ChatStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStreamService.class);

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
        StringBuffer assistantContentBuffer = new StringBuffer(1024);
        ChatTrace trace = chatObservability.startChatTrace(new ChatTraceContext(
                conversationId,
                assistantMessageId,
                "java",
                modelId,
                skillId,
                userContent
        ));
        Mono<ChatModelGateway.ChatPrompt> promptMono = Mono.zip(
                        conversationService.findConversation(conversationId),
                        skillService.getEnabledSkill(skillId),
                        modelService.getEnabledModel(modelId),
                        chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                                .map(ChatMessageEntity::toDomain)
                                .collectList(),
                        ragRetrievalService.retrieve(userContent, 5).collectList(),
                        mcpToolService.listTools().collectList()
                )
                .flatMap(tuple -> {
                    safeTraceEvent(trace, "context.loaded", Map.of(
                            "historyCount", tuple.getT4().size(),
                            "ragContextIds", tuple.getT5().stream().map(context -> context.sourceId()).toList(),
                            "mcpToolNames", tuple.getT6().stream().map(tool -> tool.name()).toList()
                    ));
                    ChatMessage userMessage = ChatMessage.user(conversationId, userContent, skillId, safeAttachmentIds);
                    return chatMessageRepository.save(ChatMessageEntity.newFromDomain(userMessage))
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
                saveAssistantMessage(
                    assistantMessageId,
                    conversationId,
                    assistantContentBuffer.toString(),
                    skillId,
                    MessageStatus.COMPLETED
            ).thenReturn(ChatStreamEvent.completed(assistantMessageId))
                    .doOnSuccess(ignored -> safeTraceComplete(trace, assistantContentBuffer.toString()))
        ).as(transactionalOperator::transactional);

        return promptMono.flatMapMany(prompt -> {
                    Flux<ChatStreamEvent> deltaFlux = chatModelGateway.stream(prompt)
                            .map(chunk -> {
                                if (chunk.type() == ChatModelGateway.ChunkType.REASONING) {
                                    return ChatStreamEvent.reasoning(assistantMessageId, chunk.content());
                                }
                                assistantContentBuffer.append(chunk.content());
                                return ChatStreamEvent.delta(assistantMessageId, chunk.content());
                            });
                    return Flux.concat(Mono.just(ChatStreamEvent.started(assistantMessageId)), deltaFlux, completedMono);
                })
                .onErrorResume(BusinessException.class, Mono::error)
                .onErrorResume(throwable -> {
                    LOGGER.error(
                            "chat stream failed, conversationId={}, skillId={}, assistantMessageId={}",
                            conversationId,
                            skillId,
                            assistantMessageId,
                            throwable
                    );
                    return saveAssistantMessage(
                            assistantMessageId,
                            conversationId,
                            assistantContentBuffer.toString(),
                            skillId,
                            MessageStatus.FAILED
                    )
                            .onErrorResume(saveError -> {
                                LOGGER.warn(
                                        "save failed assistant message failed, conversationId={}, assistantMessageId={}",
                                        conversationId,
                                        assistantMessageId,
                                        saveError
                                );
                                return Mono.empty();
                            })
                            .doOnSuccess(ignored -> safeTraceFail(
                                    trace,
                                    assistantContentBuffer.toString(),
                                    ErrorCode.CHAT_STREAM_FAILED.message()
                            ))
                            .thenReturn(ChatStreamEvent.failed(assistantMessageId, ErrorCode.CHAT_STREAM_FAILED.message()));
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
}

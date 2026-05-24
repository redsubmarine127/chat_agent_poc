package com.example.assistant.application.chat;

import com.example.assistant.application.skill.SkillService;
import com.example.assistant.application.model.ModelService;
import com.example.assistant.application.mcp.McpToolService;
import com.example.assistant.application.rag.RagRetrievalService;
import com.example.assistant.domain.chat.Conversation;
import com.example.assistant.domain.model.AiModel;
import com.example.assistant.domain.skill.Skill;
import com.example.assistant.infrastructure.persistence.ChatMessageEntity;
import com.example.assistant.infrastructure.persistence.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatStreamServiceTest {

    @Mock
    private ConversationService conversationService;
    @Mock
    private SkillService skillService;
    @Mock
    private ModelService modelService;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatModelGateway chatModelGateway;
    @Mock
    private RagRetrievalService ragRetrievalService;
    @Mock
    private McpToolService mcpToolService;
    @Mock
    private TransactionalOperator transactionalOperator;

    private ChatStreamService chatStreamService;

    @BeforeEach
    void setUp() {
        chatStreamService = new ChatStreamService(
                conversationService,
                skillService,
                modelService,
                chatMessageRepository,
                chatModelGateway,
                ragRetrievalService,
                mcpToolService,
                transactionalOperator
        );
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ragRetrievalService.retrieve(any(String.class), anyInt())).thenReturn(Flux.empty());
        when(mcpToolService.listTools()).thenReturn(Flux.empty());
    }

    @Test
    void streamShouldEmitDeltaAndSaveAssistantMessage() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = Conversation.create("测试对话");
        when(conversationService.findConversation(conversationId)).thenReturn(Mono.just(conversation));
        when(skillService.getEnabledSkill("general")).thenReturn(Mono.just(new Skill("general", "通用助手", "通用能力", true)));
        when(modelService.getEnabledModel("local-fallback")).thenReturn(Mono.just(localModel()));
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)).thenReturn(Flux.empty());
        when(chatMessageRepository.save(any(ChatMessageEntity.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(chatModelGateway.stream(any(ChatModelGateway.ChatPrompt.class))).thenReturn(Flux.just(
                ChatModelGateway.ChatModelChunk.reasoning("分析问题"),
                ChatModelGateway.ChatModelChunk.answer("你好"),
                ChatModelGateway.ChatModelChunk.answer("，世界")
        ));

        StepVerifier.create(chatStreamService.stream(conversationId, "你好", "general", "local-fallback", List.of()))
                .expectNextMatches(event -> "started".equals(event.type()))
                .expectNextMatches(event -> "reasoning".equals(event.type()) && "分析问题".equals(event.content()))
                .expectNextMatches(event -> "delta".equals(event.type()) && "你好".equals(event.content()))
                .expectNextMatches(event -> "delta".equals(event.type()) && "，世界".equals(event.content()))
                .expectNextMatches(event -> "completed".equals(event.type()))
                .verifyComplete();

        verify(chatMessageRepository, times(2)).save(any(ChatMessageEntity.class));
    }

    @Test
    void streamShouldEmitFailedEventWhenGatewayFails() {
        UUID conversationId = UUID.randomUUID();
        when(conversationService.findConversation(conversationId)).thenReturn(Mono.just(Conversation.create("测试对话")));
        when(skillService.getEnabledSkill("general")).thenReturn(Mono.just(new Skill("general", "通用助手", "通用能力", true)));
        when(modelService.getEnabledModel("local-fallback")).thenReturn(Mono.just(localModel()));
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)).thenReturn(Flux.empty());
        when(chatMessageRepository.save(any(ChatMessageEntity.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(chatModelGateway.stream(any(ChatModelGateway.ChatPrompt.class))).thenReturn(Flux.error(new IllegalStateException("boom")));

        StepVerifier.create(chatStreamService.stream(conversationId, "你好", "general", "local-fallback", List.of()))
                .expectNextMatches(event -> "started".equals(event.type()))
                .expectNextMatches(event -> "failed".equals(event.type()) && event.content().contains("对话流生成失败"))
                .verifyComplete();

        verify(chatMessageRepository, times(2)).save(any(ChatMessageEntity.class));
    }

    @Test
    void streamShouldIsolateStateForEachSubscription() {
        UUID conversationId = UUID.randomUUID();
        AtomicInteger subscriptionCounter = new AtomicInteger();
        when(conversationService.findConversation(conversationId)).thenReturn(Mono.just(Conversation.create("测试对话")));
        when(skillService.getEnabledSkill("general")).thenReturn(Mono.just(new Skill("general", "通用助手", "通用能力", true)));
        when(modelService.getEnabledModel("local-fallback")).thenReturn(Mono.just(localModel()));
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)).thenReturn(Flux.empty());
        when(chatMessageRepository.save(any(ChatMessageEntity.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(chatModelGateway.stream(any(ChatModelGateway.ChatPrompt.class))).thenAnswer(invocation ->
                Flux.just(ChatModelGateway.ChatModelChunk.answer("第%s次".formatted(subscriptionCounter.incrementAndGet()))));

        Flux<ChatStreamEvent> stream = chatStreamService.stream(conversationId, "你好", "general", "local-fallback", List.of());

        StepVerifier.create(stream.filter(event -> "delta".equals(event.type())).map(ChatStreamEvent::content))
                .expectNext("第1次")
                .verifyComplete();
        StepVerifier.create(stream.filter(event -> "delta".equals(event.type())).map(ChatStreamEvent::content))
                .expectNext("第2次")
                .verifyComplete();
    }

    private AiModel localModel() {
        return new AiModel("local-fallback", "本地回退模型", "local", "local-fallback", "", "", true);
    }
}

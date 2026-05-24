package com.example.assistant.application.skill;

import com.example.assistant.application.chat.BusinessException;
import com.example.assistant.application.chat.ErrorCode;
import com.example.assistant.domain.chat.ChatMessage;
import com.example.assistant.domain.skill.Skill;
import com.example.assistant.infrastructure.config.AssistantProperties;
import com.example.assistant.infrastructure.persistence.ChatMessageEntity;
import com.example.assistant.infrastructure.persistence.ChatMessageRepository;
import com.example.assistant.infrastructure.persistence.ConversationEntity;
import com.example.assistant.infrastructure.persistence.ConversationRepository;
import com.example.assistant.infrastructure.persistence.DynamicSkillEntity;
import com.example.assistant.infrastructure.persistence.DynamicSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private final Map<String, Skill> enabledSkillMap;
    private final DynamicSkillRepository dynamicSkillRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TransactionalOperator transactionalOperator;

    public SkillService(
            AssistantProperties properties,
            DynamicSkillRepository dynamicSkillRepository,
            ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository,
            TransactionalOperator transactionalOperator
    ) {
        this.dynamicSkillRepository = dynamicSkillRepository;
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.transactionalOperator = transactionalOperator;
        this.enabledSkillMap = properties.skills().stream()
                .filter(AssistantProperties.SkillConfig::enabled)
                .map(skill -> new Skill(skill.id().strip(), skill.name().strip(), skill.description().strip(), true))
                .collect(Collectors.toUnmodifiableMap(Skill::id, Function.identity()));
        if (enabledSkillMap.isEmpty()) {
            throw new IllegalStateException("assistant.skills must contain at least one enabled skill");
        }
    }

    public Flux<Skill> listEnabledSkills() {
        Flux<Skill> builtInSkills = Flux.fromIterable(enabledSkillMap.values())
                .sort(Comparator.comparing(Skill::id));
        Flux<Skill> dynamicSkills = dynamicSkillRepository.findByEnabledTrueOrderByCreatedAtDesc()
                .map(DynamicSkillEntity::toDomain);
        return Flux.concat(builtInSkills, dynamicSkills);
    }

    public Mono<Skill> getEnabledSkill(String skillId) {
        String selectedSkillId = skillId == null ? "" : skillId.strip();
        Mono<Skill> builtInSkillMono = Mono.justOrEmpty(enabledSkillMap.get(selectedSkillId));
        Mono<Skill> dynamicSkillMono = dynamicSkillRepository.findById(selectedSkillId)
                .filter(DynamicSkillEntity::isEnabled)
                .map(DynamicSkillEntity::toDomain);
        return builtInSkillMono.switchIfEmpty(dynamicSkillMono)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SKILL_NOT_FOUND)));
    }

    public Mono<Skill> extractFromConversation(UUID conversationId, String preferredName) {
        Mono<ConversationEntity> conversationMono = conversationRepository.findByIdAndDeletedFalse(conversationId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND)));
        Mono<List<ChatMessage>> messagesMono = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .map(ChatMessageEntity::toDomain)
                .collectList();
        return Mono.zip(conversationMono, messagesMono)
                .flatMap(tuple -> {
                    List<ChatMessage> messages = tuple.getT2().stream()
                            .filter(message -> StringUtils.hasText(message.content()))
                            .toList();
                    if (messages.isEmpty()) {
                        return Mono.error(new BusinessException(ErrorCode.SKILL_EXTRACTION_EMPTY));
                    }
                    String name = buildSkillName(preferredName, tuple.getT1().getTitle(), messages);
                    String description = buildSkillDescription(tuple.getT1().getTitle(), messages);
                    Skill skill = new Skill("custom-" + UUID.randomUUID(), name, description, true);
                    return dynamicSkillRepository.save(DynamicSkillEntity.newFromDomain(skill))
                            .map(DynamicSkillEntity::toDomain);
                })
                .as(transactionalOperator::transactional);
    }

    private String buildSkillName(String preferredName, String conversationTitle, List<ChatMessage> messages) {
        if (StringUtils.hasText(preferredName)) {
            return limitText(preferredName.strip(), 128);
        }
        if (StringUtils.hasText(conversationTitle) && !"新的对话".equals(conversationTitle.strip())) {
            return limitText("对话 Skill：" + conversationTitle.strip(), 128);
        }
        String firstUserMessage = messages.stream()
                .filter(message -> message.role().name().equals("USER"))
                .map(ChatMessage::content)
                .findFirst()
                .orElse("当前对话");
        return limitText("对话 Skill：" + firstUserMessage.replaceAll("\\s+", " ").strip(), 128);
    }

    private String buildSkillDescription(String conversationTitle, List<ChatMessage> messages) {
        String context = messages.stream()
                .limit(8)
                .map(message -> "%s：%s".formatted(message.role().name(), limitText(message.content().replaceAll("\\s+", " ").strip(), 360)))
                .collect(Collectors.joining("\n"));
        return """
                这是从对话「%s」抽取出的 Skill。使用该 Skill 时，请优先复用以下对话中形成的背景、偏好、术语、约束与解决思路，并在新问题中延续相同风格。

                抽取上下文：
                %s
                """.formatted(StringUtils.hasText(conversationTitle) ? conversationTitle.strip() : "未命名对话", context).strip();
    }

    private String limitText(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
}

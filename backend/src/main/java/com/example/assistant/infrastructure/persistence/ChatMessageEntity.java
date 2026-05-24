package com.example.assistant.infrastructure.persistence;

import com.example.assistant.domain.chat.ChatMessage;
import com.example.assistant.domain.chat.MessageRole;
import com.example.assistant.domain.chat.MessageStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Table("chat_messages")
public class ChatMessageEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    private UUID conversationId;
    private MessageRole role;
    private String content;
    private String skillId;
    private String attachmentIds;
    private MessageStatus status;
    private Instant createdAt;
    @Transient
    private boolean newEntity;

    public static ChatMessageEntity newFromDomain(ChatMessage message) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.id = message.id();
        entity.conversationId = message.conversationId();
        entity.role = message.role();
        entity.content = message.content();
        entity.skillId = message.skillId();
        entity.attachmentIds = String.join(",", message.attachmentIds().stream().map(UUID::toString).toList());
        entity.status = message.status();
        entity.createdAt = message.createdAt();
        entity.newEntity = true;
        return entity;
    }

    public ChatMessage toDomain() {
        List<UUID> attachmentIdList = StringUtils.hasText(attachmentIds)
                ? Arrays.stream(attachmentIds.split(",")).map(UUID::fromString).toList()
                : List.of();
        return new ChatMessage(id, conversationId, role, content, skillId, attachmentIdList, status, createdAt);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public MessageRole getRole() {
        return role;
    }

    public void setRole(MessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getAttachmentIds() {
        return attachmentIds;
    }

    public void setAttachmentIds(String attachmentIds) {
        this.attachmentIds = attachmentIds;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

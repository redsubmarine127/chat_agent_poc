package com.example.assistant.infrastructure.persistence;

import com.example.assistant.domain.chat.Conversation;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("conversations")
public class ConversationEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    private String title;
    private boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
    @Transient
    private boolean newEntity;

    public static ConversationEntity newFromDomain(Conversation conversation) {
        return fromDomain(conversation, true);
    }

    public static ConversationEntity existingFromDomain(Conversation conversation) {
        return fromDomain(conversation, false);
    }

    private static ConversationEntity fromDomain(Conversation conversation, boolean newEntity) {
        ConversationEntity entity = new ConversationEntity();
        entity.id = conversation.id();
        entity.title = conversation.title();
        entity.deleted = conversation.deleted();
        entity.createdAt = conversation.createdAt();
        entity.updatedAt = conversation.updatedAt();
        entity.newEntity = newEntity;
        return entity;
    }

    public Conversation toDomain() {
        return new Conversation(id, title, deleted, createdAt, updatedAt);
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

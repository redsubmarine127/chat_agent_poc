package com.example.assistant.infrastructure.persistence;

import com.example.assistant.domain.file.Attachment;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("attachments")
public class AttachmentEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    private String originalFilename;
    private String contentType;
    private String storageKey;
    private long sizeInBytes;
    private Instant createdAt;
    @Transient
    private boolean newEntity;

    public static AttachmentEntity newFromDomain(Attachment attachment) {
        AttachmentEntity entity = new AttachmentEntity();
        entity.id = attachment.id();
        entity.originalFilename = attachment.originalFilename();
        entity.contentType = attachment.contentType();
        entity.storageKey = attachment.storageKey();
        entity.sizeInBytes = attachment.sizeInBytes();
        entity.createdAt = attachment.createdAt();
        entity.newEntity = true;
        return entity;
    }

    public Attachment toDomain() {
        return new Attachment(id, originalFilename, contentType, storageKey, sizeInBytes, createdAt);
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

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public long getSizeInBytes() {
        return sizeInBytes;
    }

    public void setSizeInBytes(long sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

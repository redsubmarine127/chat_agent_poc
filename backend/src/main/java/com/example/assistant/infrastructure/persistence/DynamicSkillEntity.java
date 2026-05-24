package com.example.assistant.infrastructure.persistence;

import com.example.assistant.domain.skill.Skill;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("dynamic_skills")
public class DynamicSkillEntity implements Persistable<String> {

    @Id
    private String id;
    private String name;
    private String description;
    private boolean enabled;
    private Instant createdAt;
    @Transient
    private boolean newEntity;

    public static DynamicSkillEntity newFromDomain(Skill skill) {
        DynamicSkillEntity entity = new DynamicSkillEntity();
        entity.id = skill.id();
        entity.name = skill.name();
        entity.description = skill.description();
        entity.enabled = skill.enabled();
        entity.createdAt = Instant.now();
        entity.newEntity = true;
        return entity;
    }

    public Skill toDomain() {
        return new Skill(id, name, description, enabled);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

package com.example.assistant.interfaces.api.response;

import com.example.assistant.domain.skill.Skill;

public record SkillResponse(
        String id,
        String name,
        String description
) {

    public static SkillResponse from(Skill skill) {
        return new SkillResponse(skill.id(), skill.name(), skill.description());
    }
}

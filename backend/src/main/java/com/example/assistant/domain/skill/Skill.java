package com.example.assistant.domain.skill;

public record Skill(
        String id,
        String name,
        String description,
        boolean enabled
) {
}

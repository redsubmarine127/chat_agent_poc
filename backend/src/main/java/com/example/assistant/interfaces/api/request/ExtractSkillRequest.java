package com.example.assistant.interfaces.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ExtractSkillRequest(
        @NotNull(message = "对话 ID 不能为空")
        UUID conversationId,

        @Size(max = 128, message = "Skill 名称不能超过 128 个字符")
        String name
) {
}

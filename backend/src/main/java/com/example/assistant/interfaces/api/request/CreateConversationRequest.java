package com.example.assistant.interfaces.api.request;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 128, message = "标题长度不能超过 128 个字符")
        String title
) {
}

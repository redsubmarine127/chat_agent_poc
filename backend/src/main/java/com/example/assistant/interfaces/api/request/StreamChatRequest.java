package com.example.assistant.interfaces.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record StreamChatRequest(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 8000, message = "消息内容不能超过 8000 个字符")
        String content,

        @NotBlank(message = "Skill 不能为空")
        String skillId,

        @NotBlank(message = "模型不能为空")
        String modelId,

        @NotNull(message = "附件列表不能为空")
        @Size(max = 8, message = "单次最多携带 8 个附件")
        List<@NotNull(message = "附件 ID 不能为空") UUID> attachmentIds
) {
}

package com.example.assistant.application.rag;

import java.util.Objects;

public record RagContext(
        String sourceId,
        String title,
        String content,
        double score
) {

    public RagContext {
        sourceId = Objects.requireNonNullElse(sourceId, "");
        title = Objects.requireNonNullElse(title, "");
        content = Objects.requireNonNullElse(content, "");
        score = Math.max(0.0D, score);
    }
}

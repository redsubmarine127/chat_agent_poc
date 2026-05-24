package com.example.assistant.interfaces.api.response;

import com.example.assistant.application.rag.RagContext;

public record RagContextResponse(
        String sourceId,
        String title,
        String content,
        double score
) {

    public static RagContextResponse from(RagContext context) {
        return new RagContextResponse(context.sourceId(), context.title(), context.content(), context.score());
    }
}

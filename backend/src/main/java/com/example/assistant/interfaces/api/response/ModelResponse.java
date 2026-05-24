package com.example.assistant.interfaces.api.response;

import com.example.assistant.domain.model.AiModel;

public record ModelResponse(
        String id,
        String name,
        String provider,
        String modelName
) {

    public static ModelResponse from(AiModel model) {
        return new ModelResponse(model.id(), model.name(), model.provider(), model.modelName());
    }
}

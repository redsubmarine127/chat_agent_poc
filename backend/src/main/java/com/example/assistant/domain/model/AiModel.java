package com.example.assistant.domain.model;

public record AiModel(
        String id,
        String name,
        String provider,
        String modelName,
        String baseUrl,
        String apiKey,
        boolean enabled
) {

    public boolean localProvider() {
        return "local".equalsIgnoreCase(provider);
    }
}

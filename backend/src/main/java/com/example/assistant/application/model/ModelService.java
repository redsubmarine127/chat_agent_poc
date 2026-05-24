package com.example.assistant.application.model;

import com.example.assistant.application.chat.BusinessException;
import com.example.assistant.application.chat.ErrorCode;
import com.example.assistant.domain.model.AiModel;
import com.example.assistant.infrastructure.config.AssistantProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ModelService {

    private final String defaultModelId;
    private final List<AiModel> enabledModels;
    private final Map<String, AiModel> enabledModelMap;

    public ModelService(AssistantProperties properties) {
        this.defaultModelId = properties.defaultModelId().strip();
        this.enabledModels = properties.models().stream()
                .filter(AssistantProperties.ModelConfig::enabled)
                .map(this::toDomain)
                .sorted((left, right) -> Boolean.compare(!left.id().equals(defaultModelId), !right.id().equals(defaultModelId)))
                .toList();
        if (enabledModels.isEmpty()) {
            throw new IllegalStateException("assistant.models must contain at least one enabled model");
        }
        this.enabledModelMap = enabledModels.stream()
                .collect(Collectors.toUnmodifiableMap(AiModel::id, Function.identity()));
        if (!enabledModelMap.containsKey(defaultModelId)) {
            throw new IllegalStateException("assistant.default-model-id must reference an enabled model");
        }
    }

    public Flux<AiModel> listEnabledModels() {
        return Flux.fromIterable(enabledModels);
    }

    public Mono<AiModel> getEnabledModel(String modelId) {
        String selectedModelId = modelId == null || modelId.isBlank() ? defaultModelId : modelId.strip();
        return Mono.justOrEmpty(enabledModelMap.get(selectedModelId))
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.MODEL_NOT_FOUND)));
    }

    private AiModel toDomain(AssistantProperties.ModelConfig config) {
        return new AiModel(
                config.id().strip(),
                config.name().strip(),
                config.provider().strip(),
                config.modelName().strip(),
                StringUtils.hasText(config.baseUrl()) ? config.baseUrl().strip() : "",
                StringUtils.hasText(config.apiKey()) ? config.apiKey().strip() : "",
                config.enabled()
        );
    }
}

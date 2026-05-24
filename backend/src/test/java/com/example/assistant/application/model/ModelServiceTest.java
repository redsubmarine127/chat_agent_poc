package com.example.assistant.application.model;

import com.example.assistant.application.chat.BusinessException;
import com.example.assistant.application.chat.ErrorCode;
import com.example.assistant.infrastructure.config.AssistantProperties;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelServiceTest {

    @Test
    void constructorShouldRejectMissingDefaultModel() {
        AssistantProperties properties = new AssistantProperties(
                new AssistantProperties.Storage("./data/uploads", 1024, List.of("text/plain")),
                List.of(new AssistantProperties.SkillConfig("general", "通用助手", "通用能力", true)),
                "missing-model",
                List.of(new AssistantProperties.ModelConfig("local-fallback", "本地回退模型", "local", "local-fallback", "", "", true))
        );

        assertThatThrownBy(() -> new ModelService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default-model-id");
    }

    @Test
    void getEnabledModelShouldReturnBusinessExceptionWhenModelMissing() {
        AssistantProperties properties = new AssistantProperties(
                new AssistantProperties.Storage("./data/uploads", 1024, List.of("text/plain")),
                List.of(new AssistantProperties.SkillConfig("general", "通用助手", "通用能力", true)),
                "local-fallback",
                List.of(new AssistantProperties.ModelConfig("local-fallback", "本地回退模型", "local", "local-fallback", "", "", true))
        );
        ModelService modelService = new ModelService(properties);

        StepVerifier.create(modelService.getEnabledModel("missing-model"))
                .expectErrorMatches(throwable -> throwable instanceof BusinessException businessException
                        && businessException.errorCode() == ErrorCode.MODEL_NOT_FOUND)
                .verify();
    }
}

package com.example.assistant.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(
        @Valid Storage storage,
        @NotEmpty List<@Valid SkillConfig> skills,
        @NotBlank String defaultModelId,
        @NotEmpty List<@Valid ModelConfig> models,
        @Valid Rag rag,
        @Valid Mcp mcp
) {

    @ConstructorBinding
    public AssistantProperties {
        skills = List.copyOf(skills == null ? List.of() : skills);
        models = List.copyOf(models == null ? List.of() : models);
        rag = rag == null ? Rag.disabled() : rag;
        mcp = mcp == null ? Mcp.disabled() : mcp;
    }

    public AssistantProperties(
            Storage storage,
            List<SkillConfig> skills,
            String defaultModelId,
            List<ModelConfig> models
    ) {
        this(storage, skills, defaultModelId, models, Rag.disabled(), Mcp.disabled());
    }

    public record Storage(
            @NotBlank String rootPath,
            @Min(1) long maxFileSize,
            @NotEmpty List<@NotBlank String> allowedContentTypes
    ) {

        public Path rootPathValue() {
            return Path.of(rootPath).toAbsolutePath().normalize();
        }
    }

    public record SkillConfig(
            @NotBlank String id,
            @NotBlank String name,
            @NotBlank String description,
            boolean enabled
    ) {
    }

    public record ModelConfig(
            @NotBlank String id,
            @NotBlank String name,
            @NotBlank String provider,
            @NotBlank String modelName,
            String baseUrl,
            String apiKey,
            boolean enabled
    ) {
    }

    public record Rag(
            boolean enabled,
            @Min(1) int topK,
            List<@Valid RagDocument> documents
    ) {

        public Rag {
            topK = topK < 1 ? 5 : topK;
            documents = List.copyOf(documents == null ? List.of() : documents);
        }

        public static Rag disabled() {
            return new Rag(false, 5, List.of());
        }
    }

    public record RagDocument(
            @NotBlank String id,
            @NotBlank String title,
            @NotBlank String content
    ) {
    }

    public record Mcp(
            boolean enabled,
            List<@Valid McpTool> tools
    ) {

        public Mcp {
            tools = List.copyOf(tools == null ? List.of() : tools);
        }

        public static Mcp disabled() {
            return new Mcp(false, List.of());
        }
    }

    public record McpTool(
            @NotBlank String name,
            @NotBlank String description,
            boolean enabled
    ) {
    }
}

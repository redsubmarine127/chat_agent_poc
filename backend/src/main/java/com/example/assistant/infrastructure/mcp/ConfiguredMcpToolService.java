package com.example.assistant.infrastructure.mcp;

import com.example.assistant.application.chat.BusinessException;
import com.example.assistant.application.chat.ErrorCode;
import com.example.assistant.application.mcp.McpToolDescriptor;
import com.example.assistant.application.mcp.McpToolResult;
import com.example.assistant.application.mcp.McpToolService;
import com.example.assistant.application.rag.RagRetrievalService;
import com.example.assistant.infrastructure.config.AssistantProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class ConfiguredMcpToolService implements McpToolService {

    private static final String CONTEXT_SEARCH_TOOL = "context.search";

    private final AssistantProperties properties;
    private final RagRetrievalService ragRetrievalService;

    public ConfiguredMcpToolService(AssistantProperties properties, RagRetrievalService ragRetrievalService) {
        this.properties = properties;
        this.ragRetrievalService = ragRetrievalService;
    }

    @Override
    public Flux<McpToolDescriptor> listTools() {
        if (!properties.mcp().enabled()) {
            return Flux.empty();
        }
        return Flux.fromIterable(properties.mcp().tools())
                .filter(AssistantProperties.McpTool::enabled)
                .map(tool -> new McpToolDescriptor(tool.name(), tool.description(), true));
    }

    @Override
    public Mono<McpToolResult> invoke(String toolName, Map<String, Object> arguments) {
        if (!isEnabledTool(toolName)) {
            return Mono.error(new BusinessException(ErrorCode.MCP_TOOL_NOT_FOUND));
        }
        if (CONTEXT_SEARCH_TOOL.equals(toolName)) {
            String query = String.valueOf(arguments.getOrDefault("query", ""));
            int limit = parseLimit(arguments.get("limit"));
            return ragRetrievalService.retrieve(query, limit)
                    .collectList()
                    .map(contexts -> new McpToolResult(
                            toolName,
                            true,
                            contexts.isEmpty() ? "未检索到相关上下文。" : "已检索到 %d 条上下文。".formatted(contexts.size()),
                            Map.of("contexts", contexts)
                    ));
        }
        return Mono.just(new McpToolResult(toolName, false, "工具已注册，执行器尚未接入。", Map.of()));
    }

    private boolean isEnabledTool(String toolName) {
        if (!properties.mcp().enabled() || !StringUtils.hasText(toolName)) {
            return false;
        }
        return properties.mcp().tools().stream()
                .anyMatch(tool -> tool.enabled() && tool.name().equals(toolName));
    }

    private int parseLimit(Object value) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Math.max(1, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return properties.rag().topK();
            }
        }
        return properties.rag().topK();
    }
}

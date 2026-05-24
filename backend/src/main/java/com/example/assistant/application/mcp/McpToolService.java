package com.example.assistant.application.mcp;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface McpToolService {

    Flux<McpToolDescriptor> listTools();

    Mono<McpToolResult> invoke(String toolName, Map<String, Object> arguments);
}

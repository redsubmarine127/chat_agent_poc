package com.example.assistant.interfaces.api;

import com.example.assistant.application.mcp.McpToolService;
import com.example.assistant.interfaces.api.request.McpInvokeRequest;
import com.example.assistant.interfaces.api.response.McpToolResponse;
import com.example.assistant.interfaces.api.response.McpToolResultResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Validated
@RestController
@RequestMapping("/api/mcp/tools")
public class McpController {

    private final McpToolService mcpToolService;

    public McpController(McpToolService mcpToolService) {
        this.mcpToolService = mcpToolService;
    }

    @GetMapping
    public Flux<McpToolResponse> listTools() {
        return mcpToolService.listTools().map(McpToolResponse::from);
    }

    @PostMapping("/{toolName}/invoke")
    public Mono<McpToolResultResponse> invoke(
            @PathVariable @NotBlank String toolName,
            @RequestBody @Valid McpInvokeRequest request
    ) {
        return mcpToolService.invoke(toolName, request.arguments()).map(McpToolResultResponse::from);
    }
}

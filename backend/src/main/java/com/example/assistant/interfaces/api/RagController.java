package com.example.assistant.interfaces.api;

import com.example.assistant.application.rag.RagRetrievalService;
import com.example.assistant.interfaces.api.response.RagContextResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Validated
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagRetrievalService ragRetrievalService;

    public RagController(RagRetrievalService ragRetrievalService) {
        this.ragRetrievalService = ragRetrievalService;
    }

    @GetMapping("/search")
    public Flux<RagContextResponse> search(
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limit
    ) {
        return ragRetrievalService.retrieve(query, limit).map(RagContextResponse::from);
    }
}

package com.example.assistant.interfaces.api;

import com.example.assistant.application.model.ModelService;
import com.example.assistant.interfaces.api.response.ModelResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public Flux<ModelResponse> listModels() {
        return modelService.listEnabledModels().map(ModelResponse::from);
    }
}

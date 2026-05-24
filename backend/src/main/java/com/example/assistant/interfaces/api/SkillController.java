package com.example.assistant.interfaces.api;

import com.example.assistant.application.skill.SkillService;
import com.example.assistant.interfaces.api.request.ExtractSkillRequest;
import com.example.assistant.interfaces.api.response.SkillResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public Flux<SkillResponse> listSkills() {
        return skillService.listEnabledSkills().map(SkillResponse::from);
    }

    @PostMapping("/extractions")
    public Mono<SkillResponse> extractSkill(@Valid @RequestBody ExtractSkillRequest request) {
        return skillService.extractFromConversation(request.conversationId(), request.name()).map(SkillResponse::from);
    }
}

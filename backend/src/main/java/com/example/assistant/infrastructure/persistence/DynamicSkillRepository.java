package com.example.assistant.infrastructure.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface DynamicSkillRepository extends ReactiveCrudRepository<DynamicSkillEntity, String> {

    Flux<DynamicSkillEntity> findByEnabledTrueOrderByCreatedAtDesc();
}

package com.example.assistant.infrastructure.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.Comparator;

@Primary
@Repository
@ConditionalOnProperty(prefix = "assistant.persistence", name = "mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryDynamicSkillRepository
        extends InMemoryReactiveCrudRepository<DynamicSkillEntity, String>
        implements DynamicSkillRepository {

    @Override
    public Flux<DynamicSkillEntity> findByEnabledTrueOrderByCreatedAtDesc() {
        return values()
                .filter(DynamicSkillEntity::isEnabled)
                .sort(Comparator.comparing(
                        DynamicSkillEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed());
    }
}

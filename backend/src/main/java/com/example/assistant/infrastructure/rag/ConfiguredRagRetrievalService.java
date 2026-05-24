package com.example.assistant.infrastructure.rag;

import com.example.assistant.application.rag.RagContext;
import com.example.assistant.application.rag.RagRetrievalService;
import com.example.assistant.infrastructure.config.AssistantProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

@Service
public class ConfiguredRagRetrievalService implements RagRetrievalService {

    private final AssistantProperties properties;

    public ConfiguredRagRetrievalService(AssistantProperties properties) {
        this.properties = properties;
    }

    @Override
    public Flux<RagContext> retrieve(String query, int limit) {
        AssistantProperties.Rag rag = properties.rag();
        if (!rag.enabled() || !StringUtils.hasText(query)) {
            return Flux.empty();
        }
        int effectiveLimit = Math.min(Math.max(limit, 1), rag.topK());
        String[] terms = tokenize(query);
        return Flux.fromIterable(rag.documents())
                .map(document -> new RagContext(
                        document.id(),
                        document.title(),
                        document.content(),
                        score(document, terms)
                ))
                .filter(context -> context.score() > 0.0D)
                .sort(Comparator.comparingDouble(RagContext::score).reversed())
                .take(effectiveLimit);
    }

    private String[] tokenize(String query) {
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{IsHan}\\p{Alnum}]+"))
                .filter(StringUtils::hasText)
                .distinct()
                .toArray(String[]::new);
    }

    private double score(AssistantProperties.RagDocument document, String[] terms) {
        String searchableContent = (document.title() + "\n" + document.content()).toLowerCase(Locale.ROOT);
        long matchedCount = Arrays.stream(terms)
                .filter(searchableContent::contains)
                .count();
        if (matchedCount == 0L) {
            return 0.0D;
        }
        double titleBoost = Arrays.stream(terms)
                .filter(term -> document.title().toLowerCase(Locale.ROOT).contains(term))
                .count() * 0.5D;
        return matchedCount + titleBoost;
    }
}

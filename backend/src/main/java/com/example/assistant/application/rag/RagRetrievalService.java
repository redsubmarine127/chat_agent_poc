package com.example.assistant.application.rag;

import reactor.core.publisher.Flux;

public interface RagRetrievalService {

    Flux<RagContext> retrieve(String query, int limit);
}

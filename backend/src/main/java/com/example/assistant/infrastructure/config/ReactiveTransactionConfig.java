package com.example.assistant.infrastructure.config;

import org.reactivestreams.Publisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.reactive.TransactionCallback;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class ReactiveTransactionConfig {

    @Bean
    @ConditionalOnProperty(prefix = "assistant.persistence", name = "mode", havingValue = "database")
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "assistant.persistence", name = "mode", havingValue = "memory", matchIfMissing = true)
    public TransactionalOperator noOpTransactionalOperator() {
        return new NoOpTransactionalOperator();
    }

    private static final class NoOpTransactionalOperator implements TransactionalOperator {

        @Override
        public <T> Flux<T> transactional(Flux<T> flux) {
            return flux;
        }

        @Override
        public <T> Mono<T> transactional(Mono<T> mono) {
            return mono;
        }

        @Override
        public <T> Flux<T> execute(TransactionCallback<T> action) throws TransactionException {
            Publisher<T> publisher = action.doInTransaction(null);
            return Flux.from(publisher);
        }
    }
}

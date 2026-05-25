package com.example.assistant.infrastructure.persistence;

import org.reactivestreams.Publisher;
import org.springframework.data.domain.Persistable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

abstract class InMemoryReactiveCrudRepository<T extends Persistable<ID>, ID> implements ReactiveCrudRepository<T, ID> {

    private final Map<ID, T> store = new ConcurrentHashMap<>();

    @Override
    public <S extends T> Mono<S> save(S entity) {
        return Mono.fromSupplier(() -> {
            store.put(entity.getId(), entity);
            return entity;
        });
    }

    @Override
    public <S extends T> Flux<S> saveAll(Iterable<S> entities) {
        return Flux.fromIterable(entities).flatMap(this::save);
    }

    @Override
    public <S extends T> Flux<S> saveAll(Publisher<S> entityStream) {
        return Flux.from(entityStream).flatMap(this::save);
    }

    @Override
    public Mono<T> findById(ID id) {
        return Mono.justOrEmpty(store.get(id));
    }

    @Override
    public Mono<T> findById(Publisher<ID> id) {
        return Mono.from(id).flatMap(this::findById);
    }

    @Override
    public Mono<Boolean> existsById(ID id) {
        return Mono.fromSupplier(() -> store.containsKey(id));
    }

    @Override
    public Mono<Boolean> existsById(Publisher<ID> id) {
        return Mono.from(id).flatMap(this::existsById);
    }

    @Override
    public Flux<T> findAll() {
        return Flux.defer(() -> Flux.fromIterable(store.values()));
    }

    @Override
    public Flux<T> findAllById(Iterable<ID> ids) {
        return Flux.fromIterable(ids).flatMap(this::findById);
    }

    @Override
    public Flux<T> findAllById(Publisher<ID> idStream) {
        return Flux.from(idStream).flatMap(this::findById);
    }

    @Override
    public Mono<Long> count() {
        return Mono.fromSupplier(() -> (long) store.size());
    }

    @Override
    public Mono<Void> deleteById(ID id) {
        return Mono.fromRunnable(() -> store.remove(id)).then();
    }

    @Override
    public Mono<Void> deleteById(Publisher<ID> id) {
        return Mono.from(id).flatMap(this::deleteById);
    }

    @Override
    public Mono<Void> delete(T entity) {
        return deleteById(entity.getId());
    }

    @Override
    public Mono<Void> deleteAllById(Iterable<? extends ID> ids) {
        return Flux.fromIterable(ids).flatMap(this::deleteById).then();
    }

    @Override
    public Mono<Void> deleteAll(Iterable<? extends T> entities) {
        return Flux.fromIterable(entities).flatMap(this::delete).then();
    }

    @Override
    public Mono<Void> deleteAll(Publisher<? extends T> entityStream) {
        return Flux.from(entityStream).flatMap(this::delete).then();
    }

    @Override
    public Mono<Void> deleteAll() {
        return Mono.fromRunnable(store::clear).then();
    }

    protected Flux<T> values() {
        return findAll();
    }

    protected Mono<Integer> removeMatching(java.util.function.Predicate<T> predicate) {
        return Mono.fromSupplier(() -> {
            int beforeSize = store.size();
            store.entrySet().removeIf(entry -> predicate.test(entry.getValue()));
            return beforeSize - store.size();
        });
    }
}

package com.company.incidentdesk.persistence.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.persistence.MutableRepository;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** Reusable insertion-ordered mutable repository for in-memory adapters. */
abstract class InMemoryMutableRepository<I, V> implements MutableRepository<I, V> {
    private final Map<I, V> records = new LinkedHashMap<>();

    @Override
    public final synchronized void create(I id, V value) {
        I requiredId = Objects.requireNonNull(id, "id");
        V requiredValue = Objects.requireNonNull(value, "value");
        if (records.containsKey(requiredId)) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "record already exists");
        }
        records.put(requiredId, requiredValue);
    }

    @Override
    public final synchronized void update(I id, V value) {
        I requiredId = Objects.requireNonNull(id, "id");
        V requiredValue = Objects.requireNonNull(value, "value");
        if (!records.containsKey(requiredId)) {
            throw new RepositoryException(StorageFailureCode.NOT_FOUND, "record does not exist");
        }
        records.put(requiredId, requiredValue);
    }

    @Override
    public final synchronized Optional<V> findById(I id) {
        return Optional.ofNullable(records.get(Objects.requireNonNull(id, "id")));
    }

    @Override
    public final synchronized List<V> findAll() {
        return List.copyOf(records.values());
    }
}

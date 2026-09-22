package com.company.incidentdesk.persistence.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.persistence.AppendOnlyRepository;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** Reusable insertion-ordered append-only repository for in-memory adapters. */
abstract class InMemoryAppendOnlyRepository<I, V> implements AppendOnlyRepository<I, V> {
    private final Map<I, V> records = new LinkedHashMap<>();

    @Override
    public final synchronized void append(I id, V value) {
        I requiredId = Objects.requireNonNull(id, "id");
        V requiredValue = Objects.requireNonNull(value, "value");
        if (records.containsKey(requiredId)) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "record already exists");
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

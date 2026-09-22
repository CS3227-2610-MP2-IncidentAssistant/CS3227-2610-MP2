package com.company.incidentdesk.persistence;

import java.util.List;
import java.util.Optional;

/** Storage contract for immutable records that cannot be updated or deleted. */
public interface AppendOnlyRepository<I, V> {
    void append(I id, V value);

    Optional<V> findById(I id);

    List<V> findAll();
}

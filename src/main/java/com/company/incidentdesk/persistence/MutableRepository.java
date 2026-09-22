package com.company.incidentdesk.persistence;

import java.util.List;
import java.util.Optional;

/** Storage contract with distinct create and update operations. */
public interface MutableRepository<I, V> {
    void create(I id, V value);

    void update(I id, V value);

    Optional<V> findById(I id);

    List<V> findAll();
}

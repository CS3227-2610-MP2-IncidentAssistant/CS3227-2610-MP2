package com.company.incidentdesk.persistence.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.persistence.AuditedMutation;
import com.company.incidentdesk.persistence.AuditedMutationStore;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** In-memory atomic state-and-audit store for application and failure-path tests. */
public final class InMemoryAuditedMutationStore<S> implements AuditedMutationStore<S> {
    private final Consumer<AuditedMutation<S>> commitPreparation;
    private S currentState;
    private List<AuditEvent> auditEvents = List.of();

    public InMemoryAuditedMutationStore(S initialState) {
        this(initialState, ignored -> { });
    }

    public InMemoryAuditedMutationStore(
            S initialState,
            Consumer<AuditedMutation<S>> commitPreparation) {
        currentState = Objects.requireNonNull(initialState, "initialState");
        this.commitPreparation = Objects.requireNonNull(commitPreparation, "commitPreparation");
    }

    @Override
    public synchronized void commit(AuditedMutation<S> mutation) {
        AuditedMutation<S> requiredMutation = Objects.requireNonNull(mutation, "mutation");
        boolean duplicateEvent = auditEvents.stream()
                .anyMatch(event -> event.id().equals(requiredMutation.auditEvent().id()));
        if (duplicateEvent) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "audit event already exists");
        }

        commitPreparation.accept(requiredMutation);
        List<AuditEvent> nextAuditEvents = new ArrayList<>(auditEvents);
        nextAuditEvents.add(requiredMutation.auditEvent());

        currentState = requiredMutation.nextState();
        auditEvents = List.copyOf(nextAuditEvents);
    }

    public synchronized S currentState() {
        return currentState;
    }

    public synchronized List<AuditEvent> auditEvents() {
        return auditEvents;
    }
}

package com.company.incidentdesk.persistence;

/** Atomic persistence boundary for a domain-state replacement and its audit event. */
public interface AuditedMutationStore<S> {
    void commit(AuditedMutation<S> mutation);
}

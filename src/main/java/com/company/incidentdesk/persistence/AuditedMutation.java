package com.company.incidentdesk.persistence;

import java.util.Objects;

import com.company.incidentdesk.domain.audit.AuditEvent;

/** Complete next domain state and its audit evidence for one logical commit. */
public record AuditedMutation<S>(S nextState, AuditEvent auditEvent) {
    public AuditedMutation {
        Objects.requireNonNull(nextState, "nextState");
        Objects.requireNonNull(auditEvent, "auditEvent");
    }
}

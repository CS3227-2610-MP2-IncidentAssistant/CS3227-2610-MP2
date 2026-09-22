package com.company.incidentdesk.domain.audit;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, append-only record of one audited application operation. */
public record AuditEvent(
        AuditEventId id,
        Instant occurredAt,
        AuditActor actor,
        AuditAction action,
        AuditTarget target,
        AuditOutcome outcome,
        List<AuditChange> changes,
        Optional<AuditEvidenceReference> evidenceReference) {
    /** Chronological ordering with the event identifier as a deterministic tie-breaker. */
    public static final Comparator<AuditEvent> CHRONOLOGICAL_ORDER = Comparator
            .comparing(AuditEvent::occurredAt)
            .thenComparing(AuditEvent::id);

    public AuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(outcome, "outcome");
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        Objects.requireNonNull(evidenceReference, "evidenceReference");
    }
}

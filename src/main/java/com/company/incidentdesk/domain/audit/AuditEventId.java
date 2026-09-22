package com.company.incidentdesk.domain.audit;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier used to order and retrieve an audit event. */
public record AuditEventId(UUID value) implements Comparable<AuditEventId> {
    public AuditEventId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public int compareTo(AuditEventId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}

package com.company.incidentdesk.domain.audit;

import java.util.Objects;

/** Type and stable identifier of the resource affected by an audit event. */
public record AuditTarget(AuditTargetType type, String identifier) {
    public AuditTarget {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(identifier, "identifier");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
    }
}

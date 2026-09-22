package com.company.incidentdesk.domain.audit;

import java.util.Objects;

/** Opaque reference to detailed evidence stored in its authorized domain record. */
public record AuditEvidenceReference(AuditEvidenceType type, String identifier) {
    public AuditEvidenceReference {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(identifier, "identifier");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
    }
}

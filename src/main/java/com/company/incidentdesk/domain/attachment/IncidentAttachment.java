package com.company.incidentdesk.domain.attachment;

import java.time.Instant;
import java.util.Objects;

import com.company.incidentdesk.domain.incident.IncidentId;

/** Original display name stays in protected storage, never in anonymous display models. */
public record IncidentAttachment(AttachmentId id, IncidentId incidentId, AttachmentType type,
        long sizeBytes, String displayName, Instant createdAt) {
    public IncidentAttachment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(displayName, "displayName");
        if (sizeBytes <= 0 || sizeBytes >= Integer.MAX_VALUE || displayName.isBlank() || displayName.length() > 120
                || displayName.contains("/") || displayName.contains("\\")
                || displayName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid attachment metadata");
        }
    }

    public String storageName() { return id.value() + "." + type.extension(); }
}

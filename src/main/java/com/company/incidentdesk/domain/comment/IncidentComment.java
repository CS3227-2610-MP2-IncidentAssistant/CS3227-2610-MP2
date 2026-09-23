package com.company.incidentdesk.domain.comment;

import java.time.Instant;
import java.util.Objects;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentId;

/** Immutable comment stored with internal author identity, never a display label. */
public record IncidentComment(
        CommentId id,
        IncidentId incidentId,
        AccountId authorId,
        Role authorRole,
        Instant createdAt,
        CommentType type,
        String text) {
    public IncidentComment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(authorRole, "authorRole");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }
}

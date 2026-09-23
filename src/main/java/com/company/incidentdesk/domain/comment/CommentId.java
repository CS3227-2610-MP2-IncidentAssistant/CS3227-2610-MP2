package com.company.incidentdesk.domain.comment;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for an incident comment. */
public record CommentId(UUID value) {
    public CommentId {
        Objects.requireNonNull(value, "value");
    }
}

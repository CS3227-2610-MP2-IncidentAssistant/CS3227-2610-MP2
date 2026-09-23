package com.company.incidentdesk.application.presentation;

import java.util.Objects;
import java.time.Instant;

/** Presentation-safe incident comment. */
public record CommentModel(String authorLabel, String authorRoleLabel, String text, Instant createdAt) {
    public CommentModel {
        Objects.requireNonNull(authorLabel, "authorLabel");
        Objects.requireNonNull(authorRoleLabel, "authorRoleLabel");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}

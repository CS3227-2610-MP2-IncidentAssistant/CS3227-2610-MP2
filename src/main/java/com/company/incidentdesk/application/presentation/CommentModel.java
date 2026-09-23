package com.company.incidentdesk.application.presentation;

import java.util.Objects;

/** Presentation-safe incident comment. */
public record CommentModel(String authorLabel, String text, String createdAt) {
    public CommentModel {
        Objects.requireNonNull(authorLabel, "authorLabel");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}

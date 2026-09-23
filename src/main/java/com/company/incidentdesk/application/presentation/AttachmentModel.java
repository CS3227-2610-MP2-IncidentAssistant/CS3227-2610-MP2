package com.company.incidentdesk.application.presentation;

import java.util.Objects;

/** Authorized attachment metadata suitable for display without exposing storage paths. */
public record AttachmentModel(String id, String displayName, String mediaType, long sizeBytes) {
    public AttachmentModel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(mediaType, "mediaType");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }
}

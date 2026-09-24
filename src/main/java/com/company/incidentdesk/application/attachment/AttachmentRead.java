package com.company.incidentdesk.application.attachment;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Session-bound media capability. The URI is for the native player only, never for display or logging. */
public final class AttachmentRead {
    private final AttachmentContent content;
    private final String mediaSource;
    private final BooleanSupplier authorization;

    AttachmentRead(AttachmentContent content, String mediaSource, BooleanSupplier authorization) {
        this.content = Objects.requireNonNull(content, "content");
        this.mediaSource = Objects.requireNonNull(mediaSource, "mediaSource");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public boolean isAvailable() { return authorization.getAsBoolean(); }

    public AttachmentContent content() {
        requireAccess();
        return content;
    }

    public String mediaSource() {
        requireAccess();
        return mediaSource;
    }

    private void requireAccess() {
        if (!isAvailable()) {
            throw new SecurityException("Attachment unavailable");
        }
    }
}

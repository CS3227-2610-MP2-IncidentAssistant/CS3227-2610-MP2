package com.company.incidentdesk.application.attachment;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Session-bound image content without a storage URI. */
public final class AttachmentRead {
    private final AttachmentContent content;
    private final BooleanSupplier authorization;

    AttachmentRead(AttachmentContent content, BooleanSupplier authorization) {
        this.content = Objects.requireNonNull(content, "content");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public boolean isAvailable() { return authorization.getAsBoolean(); }

    public AttachmentContent content() {
        requireAccess();
        return content;
    }

    private void requireAccess() {
        if (!isAvailable()) {
            throw new SecurityException("Attachment unavailable");
        }
    }
}

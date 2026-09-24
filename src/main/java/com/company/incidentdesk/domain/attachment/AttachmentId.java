package com.company.incidentdesk.domain.attachment;

import java.util.Objects;
import java.util.UUID;

public record AttachmentId(UUID value) {
    public AttachmentId { Objects.requireNonNull(value, "value"); }
}

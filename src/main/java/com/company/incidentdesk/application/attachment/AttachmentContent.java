package com.company.incidentdesk.application.attachment;

import java.util.Objects;
import com.company.incidentdesk.domain.attachment.AttachmentType;

/** Authorized bytes without storage paths or original filenames. */
public record AttachmentContent(AttachmentType type, byte[] bytes) {
    public AttachmentContent {
        Objects.requireNonNull(type, "type");
        bytes = bytes.clone();
    }
    @Override public byte[] bytes() { return bytes.clone(); }
}

package com.company.incidentdesk.application.attachment;

/** A neutral validation failure; never includes source paths or filenames. */
public final class AttachmentValidationException extends RuntimeException {
    public AttachmentValidationException() { super("Unsupported or oversized attachment"); }
}

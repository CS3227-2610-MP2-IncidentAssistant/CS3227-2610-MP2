package com.company.incidentdesk.domain.attachment;

/** Explicit allowlist; file extensions are not evidence of content type. */
public enum AttachmentType {
    PNG("image/png", "png"), JPEG("image/jpeg", "jpg"), MP4("video/mp4", "mp4");

    private final String mediaType;
    private final String extension;

    AttachmentType(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() { return mediaType; }
    public String extension() { return extension; }
}

package com.company.incidentdesk.domain.attachment;

/** Persisted types. MP4 remains readable as metadata only for schema-2 compatibility. */
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

    public boolean isSupported() {
        return switch (this) {
            case PNG, JPEG -> true;
            case MP4 -> false;
        };
    }
}

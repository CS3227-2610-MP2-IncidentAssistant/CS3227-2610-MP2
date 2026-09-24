package com.company.incidentdesk.application.attachment;

/** Central, constructor-injected upload and decode limits in bytes/pixels. */
public record AttachmentLimits(long imageBytes, int count, long totalBytes, long imagePixels) {
    private static final long MIB = 1024L * 1024L;
    public static final AttachmentLimits DEFAULT = new AttachmentLimits(10 * MIB, 5, 100 * MIB, 40_000_000);

    public AttachmentLimits {
        if (imageBytes < 1 || count < 1 || totalBytes < 1 || imagePixels < 1
                || imageBytes > Integer.MAX_VALUE - 1) {
            throw new IllegalArgumentException("Invalid attachment limits");
        }
    }

}

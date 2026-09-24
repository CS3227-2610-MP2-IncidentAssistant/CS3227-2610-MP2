package com.company.incidentdesk.application.attachment;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

import com.company.incidentdesk.domain.attachment.AttachmentType;

/** Bounded file reads, signature detection, and bounded image decoding without disk caches. */
public final class AttachmentValidator {
    private final AttachmentLimits limits;

    public AttachmentValidator(AttachmentLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public byte[] readSource(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) > limits.imageBytes()) {
            throw new AttachmentValidationException();
        }
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes((int) limits.imageBytes() + 1);
            if (bytes.length == 0 || bytes.length > limits.imageBytes()) {
                throw new AttachmentValidationException();
            }
            return bytes;
        }
    }

    public AttachmentType validate(byte[] bytes, String name) throws IOException {
        AttachmentType type;
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') {
            type = AttachmentType.PNG;
        } else if (bytes.length >= 3 && bytes[0] == (byte) 0xff
                && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) {
            type = AttachmentType.JPEG;
        } else {
            throw new AttachmentValidationException();
        }
        if (bytes.length == 0 || bytes.length > limits.imageBytes() || !matchesExtension(name, type)) {
            throw new AttachmentValidationException();
        }
        try {
            validateImage(bytes, type);
        } catch (IOException exception) {
            throw new AttachmentValidationException();
        }
        return type;
    }

    public static String sanitizeName(String name) {
        String normalized = name.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1);
        StringBuilder result = new StringBuilder();
        basename.codePoints().filter(value -> !Character.isISOControl(value)
                && Character.getType(value) != Character.FORMAT).limit(100)
                .forEach(result::appendCodePoint);
        String clean = result.toString().strip();
        return clean.isBlank() ? "Attachment" : clean.substring(0, Math.min(120, clean.length()));
    }

    private boolean matchesExtension(String name, AttachmentType type) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith("." + type.extension()) || (type == AttachmentType.JPEG && lower.endsWith(".jpeg"));
    }

    private void validateImage(byte[] bytes, AttachmentType type) throws IOException {
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new AttachmentValidationException();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals(type == AttachmentType.PNG ? "png" : "jpeg")) {
                    throw new AttachmentValidationException();
                }
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels < 1 || pixels > limits.imagePixels()) {
                    throw new AttachmentValidationException();
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new AttachmentValidationException();
                }
                decoded.flush();
            } finally {
                reader.dispose();
            }
        }
    }
}

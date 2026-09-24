package com.company.incidentdesk.application.attachment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Structural codec fixtures are deliberately not playable video and do not certify native playback. */
class Mp4ValidatorTest {
    @Test
    void acceptsAvcSampleDescription() {
        assertDoesNotThrow(() -> Mp4Validator.validate(movie("avc1")));
    }

    @Test
    void rejectsUnsupportedCodecAndMalformedContainers() {
        assertThrows(AttachmentValidationException.class, () -> Mp4Validator.validate(movie("hvc1")));
        byte[] truncated = java.util.Arrays.copyOf(movie("avc1"), movie("avc1").length - 1);
        assertThrows(AttachmentValidationException.class, () -> Mp4Validator.validate(truncated));
        assertThrows(AttachmentValidationException.class,
                () -> Mp4Validator.validate(ByteBuffer.allocate(8).putInt(Integer.MAX_VALUE).putInt(0).array()));
        assertThrows(AttachmentValidationException.class, () -> Mp4Validator.validate(box("mdat", new byte[] {1})));
    }

    private static byte[] movie(String codec) {
        byte[] configuration = box("avcC", new byte[] {1, 66, 0, 30, -1, -32, 0});
        byte[] sample = box(codec, join(new byte[78], configuration));
        byte[] descriptions = box("stsd", join(ByteBuffer.allocate(8).putInt(0).putInt(1).array(), sample));
        byte[] movie = box("moov", box("trak", box("mdia", box("minf", box("stbl", descriptions)))));
        return join(box("ftyp", "isom0000".getBytes(StandardCharsets.US_ASCII)), movie, box("mdat", new byte[] {1}));
    }

    private static byte[] box(String name, byte[] content) {
        return ByteBuffer.allocate(8 + content.length).putInt(8 + content.length)
                .put(name.getBytes(StandardCharsets.US_ASCII)).put(content).array();
    }

    private static byte[] join(byte[]... parts) {
        int size = java.util.Arrays.stream(parts).mapToInt(part -> part.length).sum();
        ByteBuffer result = ByteBuffer.allocate(size);
        for (byte[] part : parts) {
            result.put(part);
        }
        return result.array();
    }
}

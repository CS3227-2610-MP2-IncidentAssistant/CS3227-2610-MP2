package com.company.incidentdesk.application.attachment;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Bounds-checks ISO BMFF boxes and allowlists AVC video / AAC sample entries, not arbitrary MP4 codecs. */
final class Mp4Validator {
    private static final int BOX_HEADER = 8;
    private static final int MAX_DEPTH = 12;
    private static final int VISUAL_SAMPLE_HEADER = 78;
    private static final int AUDIO_SAMPLE_HEADER = 28;
    private static final int ES_DESCRIPTOR = 3;
    private static final int DECODER_CONFIG_DESCRIPTOR = 4;
    private static final int MPEG4_AUDIO = 0x40;
    private boolean fileType;
    private boolean mediaData;
    private boolean avcVideo;
    private boolean movie;

    static void validate(byte[] bytes) {
        Mp4Validator validator = new Mp4Validator();
        validator.boxes(ByteBuffer.wrap(bytes), 0);
        if (!validator.fileType || !validator.mediaData || !validator.avcVideo || !validator.movie) {
            throw new AttachmentValidationException();
        }
    }

    private void boxes(ByteBuffer source, int depth) {
        if (depth > MAX_DEPTH) {
            throw new AttachmentValidationException();
        }
        while (source.hasRemaining()) {
            if (source.remaining() < BOX_HEADER) {
                throw new AttachmentValidationException();
            }
            long size = Integer.toUnsignedLong(source.getInt());
            byte[] tag = new byte[4];
            source.get(tag);
            int header = BOX_HEADER;
            if (size == 1) {
                if (source.remaining() < Long.BYTES) {
                    throw new AttachmentValidationException();
                }
                size = source.getLong();
                header += Long.BYTES;
            } else if (size == 0) {
                size = (long) source.remaining() + header;
            }
            long payloadSize = size - header;
            if (payloadSize < 0 || payloadSize > source.remaining()) {
                throw new AttachmentValidationException();
            }
            ByteBuffer payload = source.slice().limit((int) payloadSize);
            inspect(new String(tag, StandardCharsets.US_ASCII), payload, depth);
            source.position(source.position() + (int) payloadSize);
        }
    }

    private void inspect(String type, ByteBuffer payload, int depth) {
        switch (type) {
        case "ftyp" -> fileType = depth == 0 && payload.remaining() >= 8;
        case "mdat" -> mediaData = depth == 0 && payload.hasRemaining();
        case "moov" -> {
            movie = depth == 0;
            boxes(payload, depth + 1);
        }
        case "trak", "mdia", "minf", "stbl" -> boxes(payload, depth + 1);
        case "stsd" -> {
            if (depth != 5) {
                throw new AttachmentValidationException();
            }
            sampleDescriptions(payload);
        }
        default -> { /* Unknown ancillary boxes do not authorize a media codec. */ }
        }
    }

    private void sampleDescriptions(ByteBuffer payload) {
        if (payload.remaining() < 8) {
            throw new AttachmentValidationException();
        }
        payload.getInt(); // version and flags
        int count = payload.getInt();
        if (count != 1 || payload.remaining() < BOX_HEADER) {
            throw new AttachmentValidationException();
        }
        int size = payload.getInt();
        byte[] tag = new byte[4];
        payload.get(tag);
        if (size != payload.remaining() + BOX_HEADER) {
            throw new AttachmentValidationException();
        }
        switch (new String(tag, StandardCharsets.US_ASCII)) {
        case "avc1" -> {
            requireChild(payload, VISUAL_SAMPLE_HEADER, "avcC", false);
            avcVideo = true;
        }
        case "mp4a" -> requireChild(payload, AUDIO_SAMPLE_HEADER, "esds", true);
        default -> throw new AttachmentValidationException();
        }
    }

    private void requireChild(ByteBuffer payload, int fixedHeader, String expected, boolean audio) {
        if (payload.remaining() < fixedHeader + BOX_HEADER) {
            throw new AttachmentValidationException();
        }
        payload.position(payload.position() + fixedHeader);
        while (payload.remaining() >= BOX_HEADER) {
            int size = payload.getInt();
            byte[] tag = new byte[4];
            payload.get(tag);
            if (size < BOX_HEADER || size - BOX_HEADER > payload.remaining()) {
                throw new AttachmentValidationException();
            }
            ByteBuffer configuration = payload.slice().limit(size - BOX_HEADER);
            if (expected.equals(new String(tag, StandardCharsets.US_ASCII))) {
                boolean valid = audio ? hasAacDecoder(configuration)
                        : configuration.remaining() >= 7 && configuration.get(0) == 1;
                if (!valid) {
                    throw new AttachmentValidationException();
                }
                return;
            }
            payload.position(payload.position() + size - BOX_HEADER);
        }
        throw new AttachmentValidationException();
    }

    private boolean hasAacDecoder(ByteBuffer configuration) {
        if (configuration.remaining() < Integer.BYTES || configuration.getInt() != 0) {
            return false;
        }
        ByteBuffer elementaryStream = descriptor(configuration, ES_DESCRIPTOR);
        if (elementaryStream.remaining() < 3) {
            return false;
        }
        elementaryStream.getShort(); // ES_ID
        int flags = Byte.toUnsignedInt(elementaryStream.get());
        // This supported subset excludes dependent streams, URL streams and OCR streams.
        if ((flags & 0xe0) != 0) {
            return false;
        }
        ByteBuffer decoder = descriptor(elementaryStream, DECODER_CONFIG_DESCRIPTOR);
        return decoder.remaining() >= 13 && Byte.toUnsignedInt(decoder.get()) == MPEG4_AUDIO;
    }

    private ByteBuffer descriptor(ByteBuffer source, int expectedTag) {
        if (!source.hasRemaining() || Byte.toUnsignedInt(source.get()) != expectedTag) {
            throw new AttachmentValidationException();
        }
        int size = 0;
        for (int octet = 0; octet < 4 && source.hasRemaining(); octet++) {
            int value = Byte.toUnsignedInt(source.get());
            size = (size << 7) | (value & 0x7f);
            if ((value & 0x80) == 0) {
                if (size > source.remaining()) {
                    throw new AttachmentValidationException();
                }
                return source.slice().limit(size);
            }
        }
        throw new AttachmentValidationException();
    }
}

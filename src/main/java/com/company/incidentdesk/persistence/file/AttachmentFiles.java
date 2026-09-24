package com.company.incidentdesk.persistence.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Set;
import java.util.UUID;

import com.company.incidentdesk.domain.attachment.AttachmentId;
import com.company.incidentdesk.domain.attachment.AttachmentType;
import com.company.incidentdesk.domain.attachment.IncidentAttachment;

/** Immutable blobs with per-upload recovery markers. Never scans/deletes unmarked user files. */
final class AttachmentFiles {
    private static final String MARKER_PREFIX = ".pending-";
    private final Path directory;

    AttachmentFiles(Path dataDirectory) throws IOException {
        directory = dataDirectory.toRealPath().resolve("attachments");
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(directory);
        }
        requireDirectory();
    }

    void prepare(IncidentAttachment attachment, byte[] content) throws IOException {
        requireDirectory();
        Path marker = marker(attachment.id());
        Path temporary = temporary(attachment.id());
        Path destination = directory.resolve(attachment.storageName());
        if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Attachment identifier already exists");
        }
        for (AttachmentType type : AttachmentType.values()) {
            if (Files.exists(directory.resolve(attachment.id().value() + "." + type.extension()),
                    LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Attachment identifier already exists");
            }
        }
        writeNew(marker, new byte[0]);
        try {
            forceDirectory();
            writeNew(temporary, content);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination);
            }
            forceDirectory();
        } catch (IOException exception) {
            try {
                finish(attachment.id(), false);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    boolean hasPending() throws IOException {
        requireDirectory();
        try (var markers = Files.newDirectoryStream(directory, MARKER_PREFIX + "*")) {
            return markers.iterator().hasNext();
        }
    }

    byte[] read(IncidentAttachment attachment) throws IOException {
        requireDirectory();
        Path path = directory.resolve(attachment.storageName());
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != attachment.sizeBytes()) {
            throw new IOException("Attachment unavailable");
        }
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] content = input.readNBytes(Math.toIntExact(attachment.sizeBytes()) + 1);
            if (content.length != attachment.sizeBytes()) {
                throw new IOException("Attachment unavailable");
            }
            return content;
        }
    }

    void finish(AttachmentId id, boolean committed) throws IOException {
        requireDirectory();
        // The marker proves ownership of these two generated paths, never of arbitrary directory contents.
        if (!Files.isRegularFile(marker(id), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.deleteIfExists(temporary(id));
        if (!committed) {
            for (AttachmentType type : AttachmentType.values()) {
                Files.deleteIfExists(directory.resolve(id.value() + "." + type.extension()));
            }
        }
        Files.delete(marker(id));
        forceDirectory();
    }

    void recover(Set<AttachmentId> referenced) throws IOException {
        requireDirectory();
        try (var markers = Files.newDirectoryStream(directory, MARKER_PREFIX + "*")) {
            for (Path path : markers) {
                String name = path.getFileName().toString().substring(MARKER_PREFIX.length());
                AttachmentId id;
                try {
                    id = new AttachmentId(UUID.fromString(name));
                } catch (IllegalArgumentException exception) {
                    continue; // Unrecognized entries are not owned by this protocol.
                }
                if (path.equals(marker(id))) {
                    finish(id, referenced.contains(id));
                }
            }
        }
    }

    private Path marker(AttachmentId id) { return directory.resolve(MARKER_PREFIX + id.value()); }
    private Path temporary(AttachmentId id) { return directory.resolve(".upload-" + id.value()); }

    private void requireDirectory() throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !directory.equals(directory.toRealPath())) {
            throw new IOException("Attachment storage unavailable");
        }
    }

    private void writeNew(Path path, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer bytes = ByteBuffer.wrap(content);
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            channel.force(true);
        }
    }

    private void forceDirectory() {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException exception) {
            // Directory fsync is unavailable on some target platforms; files are flushed independently.
        }
    }
}

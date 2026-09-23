package com.company.incidentdesk.persistence.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** Same-filesystem, validated, atomic persistence with one bounded known-good backup. */
public final class RecoverySafeFile<T> {
    private final Path canonicalFile;
    private final Path backupFile;
    private final DataCodec<T> codec;

    public RecoverySafeFile(Path dataDirectory, String fileName, DataCodec<T> codec) {
        Path normalizedDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory")
                .toAbsolutePath().normalize();
        this.canonicalFile = normalizedDirectory.resolve(requireSimpleName(fileName));
        this.backupFile = normalizedDirectory.resolve(fileName + ".bak");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public synchronized Optional<T> load() {
        if (Files.notExists(canonicalFile)) {
            return Optional.empty();
        }
        return Optional.of(readAndValidate(canonicalFile));
    }

    public synchronized Optional<T> loadBackup() {
        if (Files.notExists(backupFile)) {
            return Optional.empty();
        }
        return Optional.of(readAndValidate(backupFile));
    }

    public synchronized void save(T value) {
        Objects.requireNonNull(value, "value");
        Path temporary = canonicalFile.resolveSibling(canonicalFile.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.createDirectories(canonicalFile.getParent());
            byte[] encoded = codec.encode(value);
            writeAndFlush(temporary, encoded);
            T validated = codec.decode(Files.readAllBytes(temporary));
            Objects.requireNonNull(validated, "decoded value");
            rotateCanonicalToBackup();
            replaceCanonical(temporary);
            forceDirectory(canonicalFile.getParent());
        } catch (RepositoryException exception) {
            deleteQuietly(temporary);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(temporary);
            throw new RepositoryException(StorageFailureCode.STORAGE_UNAVAILABLE,
                    "application data could not be committed", exception);
        }
    }

    public synchronized T restoreBackup() {
        T backup = loadBackup().orElseThrow(() ->
                new RepositoryException(StorageFailureCode.NOT_FOUND, "no known-good backup exists"));
        Path temporary = canonicalFile.resolveSibling(canonicalFile.getFileName() + ".tmp-" + UUID.randomUUID());
        Path preservedCanonical = canonicalFile.resolveSibling(canonicalFile.getFileName() + ".corrupt");
        try {
            byte[] encoded = codec.encode(backup);
            writeAndFlush(temporary, encoded);
            codec.decode(Files.readAllBytes(temporary));
            if (Files.exists(canonicalFile)) {
                Files.copy(canonicalFile, preservedCanonical, StandardCopyOption.REPLACE_EXISTING);
                forceFile(preservedCanonical);
            }
            replaceCanonical(temporary);
            forceDirectory(canonicalFile.getParent());
            return backup;
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(temporary);
            throw new RepositoryException(StorageFailureCode.STORAGE_UNAVAILABLE,
                    "known-good backup could not be restored", exception);
        }
    }

    private T readAndValidate(Path file) {
        try {
            return Objects.requireNonNull(codec.decode(Files.readAllBytes(file)), "decoded value");
        } catch (RepositoryException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new RepositoryException(StorageFailureCode.CORRUPT_DATA,
                    "application data is corrupt", exception);
        }
    }

    private void rotateCanonicalToBackup() throws IOException {
        if (Files.exists(canonicalFile)) {
            Files.copy(canonicalFile, backupFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            forceFile(backupFile);
        }
    }

    private void replaceCanonical(Path temporary) throws IOException {
        try {
            Files.move(temporary, canonicalFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, canonicalFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeAndFlush(Path file, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Some platforms do not expose directory fsync; the file itself is already durable.
        }
    }

    private static String requireSimpleName(String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        if (fileName.isBlank() || !Path.of(fileName).getFileName().toString().equals(fileName)) {
            throw new IllegalArgumentException("fileName must be a simple file name");
        }
        return fileName;
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // The unique orphan is ignored on startup and can be diagnosed later.
        }
    }
}

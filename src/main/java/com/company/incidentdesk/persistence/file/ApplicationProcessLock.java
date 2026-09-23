package com.company.incidentdesk.persistence.file;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** Exclusive process lock which prevents a second application writer. */
public final class ApplicationProcessLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    private ApplicationProcessLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static ApplicationProcessLock acquire(Path dataDirectory) {
        FileChannel channel = null;
        try {
            Files.createDirectories(dataDirectory);
            channel = FileChannel.open(dataDirectory.resolve("incident-desk.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw activeWriterFailure();
            }
            return new ApplicationProcessLock(channel, lock);
        } catch (OverlappingFileLockException exception) {
            closeQuietly(channel);
            throw activeWriterFailure();
        } catch (IOException exception) {
            closeQuietly(channel);
            throw new RepositoryException(StorageFailureCode.STORAGE_UNAVAILABLE,
                    "application data lock is unavailable", exception);
        }
    }

    @Override
    public void close() {
        try {
            lock.release();
            channel.close();
        } catch (IOException exception) {
            throw new RepositoryException(StorageFailureCode.STORAGE_UNAVAILABLE,
                    "application data lock could not be released", exception);
        }
    }

    private static RepositoryException activeWriterFailure() {
        return new RepositoryException(StorageFailureCode.WRITER_ALREADY_ACTIVE,
                "another Incident Desk writer is already active");
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // Preserve the original acquisition failure.
            }
        }
    }
}

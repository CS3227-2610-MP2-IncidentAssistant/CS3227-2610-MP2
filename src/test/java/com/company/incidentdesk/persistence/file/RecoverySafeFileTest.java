package com.company.incidentdesk.persistence.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

class RecoverySafeFileTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void failedEncodingPreservesCanonicalAndKnownGoodBackup() {
        ToggleCodec codec = new ToggleCodec();
        RecoverySafeFile<String> file = new RecoverySafeFile<>(temporaryDirectory, "state.dat", codec);
        file.save("first");
        file.save("second");

        codec.failWrites = true;
        RepositoryException failure = assertThrows(RepositoryException.class, () -> file.save("third"));

        assertEquals(StorageFailureCode.STORAGE_UNAVAILABLE, failure.code());
        codec.failWrites = false;
        assertEquals("second", file.load().orElseThrow());
        assertEquals("first", file.loadBackup().orElseThrow());
    }

    @Test
    void corruptCanonicalIsReportedWithoutBeingReset() throws IOException {
        RecoverySafeFile<String> file = new RecoverySafeFile<>(temporaryDirectory, "state.dat", new ToggleCodec());
        Files.writeString(temporaryDirectory.resolve("state.dat"), "bad", StandardCharsets.UTF_8);

        RepositoryException failure = assertThrows(RepositoryException.class, file::load);

        assertEquals(StorageFailureCode.CORRUPT_DATA, failure.code());
        assertEquals("bad", Files.readString(temporaryDirectory.resolve("state.dat")));
    }

    @Test
    void unknownNewerAggregateSchemaIsRejected() throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(8).putInt(0x49444B31).putInt(2);
        Files.write(temporaryDirectory.resolve(LocalApplicationStore.STATE_FILE_NAME), bytes.array());

        RepositoryException failure = assertThrows(RepositoryException.class,
                () -> new LocalApplicationStore(temporaryDirectory));

        assertEquals(StorageFailureCode.UNSUPPORTED_SCHEMA, failure.code());
    }

    @Test
    void secondWriterIsRefusedUntilFirstCloses() {
        LocalApplicationStore first = new LocalApplicationStore(temporaryDirectory);
        try {
            RepositoryException failure = assertThrows(RepositoryException.class,
                    () -> new LocalApplicationStore(temporaryDirectory));
            assertEquals(StorageFailureCode.WRITER_ALREADY_ACTIVE, failure.code());
        } finally {
            first.close();
        }

        try (LocalApplicationStore ignored = new LocalApplicationStore(temporaryDirectory)) {
            // Lock was released and can be reacquired.
        }
    }

    private static final class ToggleCodec implements DataCodec<String> {
        private boolean failWrites;

        @Override
        public byte[] encode(String value) throws IOException {
            if (failWrites) {
                throw new IOException("simulated write preparation failure");
            }
            return ("valid:" + value).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String decode(byte[] bytes) throws IOException {
            String value = new String(bytes, StandardCharsets.UTF_8);
            if (!value.startsWith("valid:")) {
                throw new IOException("corrupt fixture");
            }
            return value.substring("valid:".length());
        }
    }
}

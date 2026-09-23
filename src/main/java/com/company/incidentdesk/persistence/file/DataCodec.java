package com.company.incidentdesk.persistence.file;

import java.io.IOException;

/** Validating serialization boundary used by the recovery-safe file store. */
public interface DataCodec<T> {
    byte[] encode(T value) throws IOException;

    T decode(byte[] bytes) throws IOException;
}

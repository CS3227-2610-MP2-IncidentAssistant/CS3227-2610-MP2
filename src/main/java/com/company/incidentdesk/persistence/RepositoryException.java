package com.company.incidentdesk.persistence;

import java.util.Objects;

/** Structured repository failure that does not expose paths or serialized data. */
public final class RepositoryException extends RuntimeException {
    private final StorageFailureCode code;

    /**
     * Creates a repository failure with a stable code.
     *
     * @param code failure category
     * @param message diagnostic message without sensitive data
     */
    public RepositoryException(StorageFailureCode code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = Objects.requireNonNull(code, "code");
    }

    /**
     * Creates a repository failure caused by the storage implementation.
     *
     * @param code failure category
     * @param message diagnostic message without sensitive data
     * @param cause underlying storage failure
     */
    public RepositoryException(StorageFailureCode code, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public StorageFailureCode code() {
        return code;
    }
}

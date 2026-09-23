package com.company.incidentdesk.persistence;

/** Stable categories for repository and storage failures. */
public enum StorageFailureCode {
    ALREADY_EXISTS,
    NOT_FOUND,
    STORAGE_UNAVAILABLE,
    CORRUPT_DATA,
    UNSUPPORTED_SCHEMA,
    WRITER_ALREADY_ACTIVE
}

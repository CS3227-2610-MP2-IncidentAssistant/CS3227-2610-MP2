package com.company.incidentdesk.application.result;

/** Stable categories for expected application failures. */
public enum ApplicationErrorCode {
    VALIDATION,
    ACCESS_DENIED,
    RESOURCE_UNAVAILABLE,
    INVALID_STATE,
    PERSISTENCE_FAILURE,
    CORRUPT_DATA;

    /**
     * Returns the code safe to expose through presentation models.
     *
     * @return neutral presentation-safe code
     */
    public ApplicationErrorCode presentationCode() {
        return switch (this) {
        case VALIDATION -> VALIDATION;
        case ACCESS_DENIED -> RESOURCE_UNAVAILABLE;
        case RESOURCE_UNAVAILABLE -> RESOURCE_UNAVAILABLE;
        case INVALID_STATE -> INVALID_STATE;
        case PERSISTENCE_FAILURE -> PERSISTENCE_FAILURE;
        case CORRUPT_DATA -> CORRUPT_DATA;
        };
    }
}

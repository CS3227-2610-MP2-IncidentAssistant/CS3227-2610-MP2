package com.company.incidentdesk.application.result;

import java.util.Objects;
import java.util.Optional;

/** Failed application result containing a structured error. */
record ApplicationFailure<T>(ApplicationError applicationError) implements ApplicationResult<T> {
    /**
     * Creates a failed result.
     *
     * @param applicationError structured application error
     */
    public ApplicationFailure {
        Objects.requireNonNull(applicationError, "applicationError");
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public Optional<T> value() {
        return Optional.empty();
    }

    @Override
    public Optional<ApplicationError> error() {
        return Optional.of(applicationError);
    }
}

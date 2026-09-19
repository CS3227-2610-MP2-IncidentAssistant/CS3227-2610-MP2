package com.company.incidentdesk.application.result;

import java.util.Objects;
import java.util.Optional;

/** Successful application result containing a non-null value. */
record ApplicationSuccess<T>(T result) implements ApplicationResult<T> {
    /**
     * Creates a successful result.
     *
     * @param result non-null result value
     */
    public ApplicationSuccess {
        Objects.requireNonNull(result, "result");
    }

    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public Optional<T> value() {
        return Optional.of(result);
    }

    @Override
    public Optional<ApplicationError> error() {
        return Optional.empty();
    }
}

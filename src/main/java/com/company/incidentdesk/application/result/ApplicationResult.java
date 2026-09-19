package com.company.incidentdesk.application.result;

import java.util.Objects;
import java.util.Optional;

/** Typed success or expected failure returned by application services. */
public sealed interface ApplicationResult<T>
        permits ApplicationSuccess, ApplicationFailure {
    /**
     * Creates a successful result.
     *
     * @param value non-null result value
     * @param <T> result value type
     * @return successful application result
     */
    static <T> ApplicationResult<T> success(T value) {
        return new ApplicationSuccess<>(value);
    }

    /**
     * Creates a failed result.
     *
     * @param error structured application error
     * @param <T> expected success value type
     * @return failed application result
     */
    static <T> ApplicationResult<T> failure(ApplicationError error) {
        ApplicationError presentationError = Objects.requireNonNull(error, "error").forPresentation();
        return new ApplicationFailure<>(presentationError);
    }

    /**
     * Checks whether the operation succeeded.
     *
     * @return true for a successful result
     */
    boolean isSuccess();

    /**
     * Returns the success value when present.
     *
     * @return success value or empty for a failure
     */
    Optional<T> value();

    /**
     * Returns the application error when present.
     *
     * @return application error or empty for success
     */
    Optional<ApplicationError> error();
}

package com.company.incidentdesk.application.result;

import java.util.Objects;

import com.company.incidentdesk.application.validation.ValidationResult;

/** Structured application failure without user-facing or sensitive text. */
public record ApplicationError(ApplicationErrorCode code, ValidationResult validation) {
    /**
     * Creates an application error with consistent validation details.
     *
     * @param code stable error category
     * @param validation field errors for validation failures only
     */
    public ApplicationError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(validation, "validation");
        if (code == ApplicationErrorCode.VALIDATION && validation.isValid()) {
            throw new IllegalArgumentException("validation error requires field errors");
        }
        if (code != ApplicationErrorCode.VALIDATION && !validation.isValid()) {
            throw new IllegalArgumentException("field errors are allowed only for validation failures");
        }
    }

    /**
     * Creates a field-validation application error.
     *
     * @param validation invalid field result
     * @return validation application error
     */
    public static ApplicationError validation(ValidationResult validation) {
        return new ApplicationError(ApplicationErrorCode.VALIDATION, validation);
    }

    /**
     * Creates a non-validation application error.
     *
     * @param code non-validation error category
     * @return application error without field details
     */
    public static ApplicationError of(ApplicationErrorCode code) {
        return new ApplicationError(code, ValidationResult.valid());
    }

    /**
     * Returns a copy safe for presentation to an application user.
     *
     * @return error with denial mapped to neutral resource unavailability
     */
    public ApplicationError forPresentation() {
        ApplicationErrorCode presentationCode = code.presentationCode();
        if (presentationCode == code) {
            return this;
        }
        return ApplicationError.of(presentationCode);
    }
}

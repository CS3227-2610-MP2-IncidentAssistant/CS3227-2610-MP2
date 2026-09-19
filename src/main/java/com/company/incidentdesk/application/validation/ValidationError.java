package com.company.incidentdesk.application.validation;

import java.util.Objects;

/** Associates a stable validation failure with a form field. */
public record ValidationError(ValidationField field, ValidationErrorCode code) {
    /**
     * Creates a validation error.
     *
     * @param field field containing the invalid value
     * @param code stable error code
     */
    public ValidationError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(code, "code");
    }
}

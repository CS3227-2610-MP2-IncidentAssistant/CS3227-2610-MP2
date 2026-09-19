package com.company.incidentdesk.application.validation;

import java.util.Objects;

/** Validates text fields that must contain non-whitespace content. */
public final class RequiredTextValidator {
    /**
     * Validates a required text value without normalizing it.
     *
     * @param field field being validated
     * @param value candidate value, which may be null
     * @return valid or field-associated required error
     */
    public ValidationResult validate(ValidationField field, String value) {
        Objects.requireNonNull(field, "field");
        if (value == null || value.isBlank()) {
            return ValidationResult.invalid(new ValidationError(field, ValidationErrorCode.REQUIRED));
        }
        return ValidationResult.valid();
    }
}

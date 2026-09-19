package com.company.incidentdesk.application.validation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Immutable ordered collection of field-level validation errors. */
public record ValidationResult(List<ValidationError> errors) {
    private static final ValidationResult VALID = new ValidationResult(List.of());

    /**
     * Creates a validation result and defensively copies its errors.
     *
     * @param errors ordered validation errors
     */
    public ValidationResult(List<ValidationError> errors) {
        this.errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
    }

    /**
     * Returns a valid result with no errors.
     *
     * @return shared immutable valid result
     */
    public static ValidationResult valid() {
        return VALID;
    }

    /**
     * Creates an invalid result from one or more errors.
     *
     * @param errors validation errors
     * @return immutable invalid result
     */
    public static ValidationResult invalid(ValidationError... errors) {
        Objects.requireNonNull(errors, "errors");
        if (errors.length == 0) {
            throw new IllegalArgumentException("invalid result requires at least one error");
        }
        return new ValidationResult(Arrays.asList(errors));
    }

    /**
     * Checks whether validation produced no errors.
     *
     * @return true when the result is valid
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Combines this result with another while preserving error order.
     *
     * @param other result to append
     * @return combined immutable validation result
     */
    public ValidationResult combine(ValidationResult other) {
        Objects.requireNonNull(other, "other");
        List<ValidationError> combinedErrors = Stream.concat(errors.stream(), other.errors.stream()).toList();
        return combinedErrors.isEmpty() ? valid() : new ValidationResult(combinedErrors);
    }

    /**
     * Returns validation errors associated with one field.
     *
     * @param field field to select
     * @return immutable ordered errors for the field
     */
    public List<ValidationError> errorsFor(ValidationField field) {
        Objects.requireNonNull(field, "field");
        return errors.stream().filter(error -> error.field().equals(field)).toList();
    }
}

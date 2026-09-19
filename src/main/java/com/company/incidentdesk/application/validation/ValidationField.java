package com.company.incidentdesk.application.validation;

import java.util.Objects;

/** Stable form-field identifier independent of presentation controls. */
public record ValidationField(String value) {
    /**
     * Creates a field identifier.
     *
     * @param value non-blank stable identifier
     */
    public ValidationField {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}

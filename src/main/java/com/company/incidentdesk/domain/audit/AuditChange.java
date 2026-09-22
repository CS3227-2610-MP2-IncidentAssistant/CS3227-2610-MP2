package com.company.incidentdesk.domain.audit;

import java.util.Objects;
import java.util.Optional;

/** One before/after entry in a structured, privacy-safe audit summary. */
public record AuditChange(
        AuditChangeField field,
        Optional<String> beforeValue,
        Optional<String> afterValue) {
    public AuditChange {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(beforeValue, "beforeValue");
        Objects.requireNonNull(afterValue, "afterValue");
        if (beforeValue.isEmpty() && afterValue.isEmpty()) {
            throw new IllegalArgumentException("an audit change requires a before or after value");
        }
        beforeValue.ifPresent(value -> requireValue(value, "beforeValue"));
        afterValue.ifPresent(value -> requireValue(value, "afterValue"));
    }

    public static AuditChange changed(AuditChangeField field, String beforeValue, String afterValue) {
        return new AuditChange(field, Optional.of(beforeValue), Optional.of(afterValue));
    }

    public static AuditChange added(AuditChangeField field, String afterValue) {
        return new AuditChange(field, Optional.empty(), Optional.of(afterValue));
    }

    private static void requireValue(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}

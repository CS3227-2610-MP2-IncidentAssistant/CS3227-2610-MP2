package com.company.incidentdesk.application.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Tests validation result composition and immutability. */
class ValidationResultTest {
    private static final ValidationField TITLE = new ValidationField("title");
    private static final ValidationField DESCRIPTION = new ValidationField("description");
    private static final ValidationError TITLE_REQUIRED = required(TITLE);
    private static final ValidationError DESCRIPTION_REQUIRED = required(DESCRIPTION);

    @Test
    void validResultContainsNoErrors() {
        ValidationResult result = ValidationResult.valid();

        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void invalidFactoryRequiresAtLeastOneError() {
        assertThrows(IllegalArgumentException.class, ValidationResult::invalid);
    }

    @Test
    void combinesResultsInDeterministicOrder() {
        ValidationResult titleResult = ValidationResult.invalid(TITLE_REQUIRED);
        ValidationResult descriptionResult = ValidationResult.invalid(DESCRIPTION_REQUIRED);

        ValidationResult combined = titleResult.combine(descriptionResult);

        assertFalse(combined.isValid());
        assertEquals(List.of(TITLE_REQUIRED, DESCRIPTION_REQUIRED), combined.errors());
    }

    @Test
    void filtersErrorsByField() {
        ValidationResult result = ValidationResult.invalid(TITLE_REQUIRED, DESCRIPTION_REQUIRED);

        assertEquals(List.of(TITLE_REQUIRED), result.errorsFor(TITLE));
        assertEquals(List.of(DESCRIPTION_REQUIRED), result.errorsFor(DESCRIPTION));
    }

    @Test
    void defensivelyCopiesAndExposesImmutableErrors() {
        List<ValidationError> source = new ArrayList<>(List.of(TITLE_REQUIRED));
        ValidationResult result = new ValidationResult(source);

        source.clear();

        assertEquals(List.of(TITLE_REQUIRED), result.errors());
        assertThrows(UnsupportedOperationException.class, () -> result.errors().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.errorsFor(TITLE).clear());
    }

    private static ValidationError required(ValidationField field) {
        return new ValidationError(field, ValidationErrorCode.REQUIRED);
    }
}

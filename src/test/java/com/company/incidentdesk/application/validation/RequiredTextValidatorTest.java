package com.company.incidentdesk.application.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests reusable required-text validation. */
class RequiredTextValidatorTest {
    private static final ValidationField TITLE = new ValidationField("title");

    private final RequiredTextValidator validator = new RequiredTextValidator();

    @Test
    void rejectsNullEmptyAndWhitespaceOnlyValues() {
        assertRequired(validator.validate(TITLE, null));
        assertRequired(validator.validate(TITLE, ""));
        assertRequired(validator.validate(TITLE, " \n\t "));
    }

    @Test
    void acceptsTextWithoutRequiringNormalization() {
        String enteredText = "  Incident title  ";

        ValidationResult result = validator.validate(TITLE, enteredText);

        assertTrue(result.isValid());
        assertEquals("  Incident title  ", enteredText);
    }

    private void assertRequired(ValidationResult result) {
        ValidationError error = result.errors().getFirst();
        assertEquals(TITLE, error.field());
        assertEquals(ValidationErrorCode.REQUIRED, error.code());
    }
}

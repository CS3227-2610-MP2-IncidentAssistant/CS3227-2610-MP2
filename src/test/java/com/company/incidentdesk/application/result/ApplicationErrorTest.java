package com.company.incidentdesk.application.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.validation.ValidationError;
import com.company.incidentdesk.application.validation.ValidationErrorCode;
import com.company.incidentdesk.application.validation.ValidationField;
import com.company.incidentdesk.application.validation.ValidationResult;

/** Tests structured application errors and privacy-safe presentation mapping. */
class ApplicationErrorTest {
    private static final ValidationResult INVALID_TITLE = ValidationResult.invalid(
            new ValidationError(new ValidationField("title"), ValidationErrorCode.REQUIRED));

    @Test
    void validationErrorRequiresFieldErrors() {
        ApplicationError error = ApplicationError.validation(INVALID_TITLE);

        assertEquals(ApplicationErrorCode.VALIDATION, error.code());
        assertEquals(INVALID_TITLE, error.validation());
        assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationError.validation(ValidationResult.valid()));
    }

    @Test
    void nonValidationErrorRejectsFieldErrors() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApplicationError(ApplicationErrorCode.INVALID_STATE, INVALID_TITLE));
        assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationError.of(ApplicationErrorCode.VALIDATION));
    }

    @Test
    void denialAndMissingResourceHaveSamePresentationCode() {
        ApplicationError denied = ApplicationError.of(ApplicationErrorCode.ACCESS_DENIED);
        ApplicationError unavailable = ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE);

        assertEquals(unavailable, denied.forPresentation());
        assertSame(unavailable, unavailable.forPresentation());
    }

    @Test
    void nonSensitiveErrorsRemainUnchangedForPresentation() {
        for (ApplicationErrorCode code : ApplicationErrorCode.values()) {
            if (code != ApplicationErrorCode.VALIDATION && code != ApplicationErrorCode.ACCESS_DENIED) {
                ApplicationError error = ApplicationError.of(code);
                assertSame(error, error.forPresentation());
            }
        }
    }
}

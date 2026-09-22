package com.company.incidentdesk.ui.reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.validation.ValidationError;
import com.company.incidentdesk.application.validation.ValidationErrorCode;
import com.company.incidentdesk.application.validation.ValidationField;
import com.company.incidentdesk.application.validation.ValidationResult;
import com.company.incidentdesk.domain.incident.IncidentCategory;

class IncidentSubmissionFormTest {
    @Test
    void rejectsWhitespaceOnlyRequiredTextWithoutSubmitting() {
        List<IncidentSubmissionForm.Submission> submissions = new ArrayList<>();

        ValidationResult result = IncidentSubmissionForm.validateAndSubmit(
                new IncidentSubmissionForm.Submission("   ", "Printer is jammed", IncidentCategory.IT),
                submissions::add);

        assertFalse(result.isValid());
        assertEquals(
                List.of(new ValidationError(
                        new ValidationField("title"),
                        ValidationErrorCode.REQUIRED)),
                result.errors());
        assertEquals(List.of(), submissions);
    }

    @Test
    void forwardsValidFieldValuesOnceWhenSubmitted() {
        List<IncidentSubmissionForm.Submission> submissions = new ArrayList<>();
        IncidentSubmissionForm.Submission submission = new IncidentSubmissionForm.Submission(
                "Printer failure",
                "  Printer remains jammed.\n",
                IncidentCategory.IT);

        ValidationResult result = IncidentSubmissionForm.validateAndSubmit(
                submission,
                submissions::add);

        assertEquals(ValidationResult.valid(), result);
        assertEquals(
                List.of(submission),
                submissions);
    }

    @Test
    void rejectsMissingCategoryWithoutSubmitting() {
        List<IncidentSubmissionForm.Submission> submissions = new ArrayList<>();

        ValidationResult result = IncidentSubmissionForm.validateAndSubmit(
                new IncidentSubmissionForm.Submission("Printer failure", "Printer is jammed", null),
                submissions::add);

        assertFalse(result.isValid());
        assertEquals(
                List.of(new ValidationError(
                        new ValidationField("category"),
                        ValidationErrorCode.REQUIRED)),
                result.errors());
        assertEquals(List.of(), submissions);
    }
}

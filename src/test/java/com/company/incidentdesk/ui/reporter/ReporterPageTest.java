package com.company.incidentdesk.ui.reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.incident.IncidentView;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.validation.ValidationError;
import com.company.incidentdesk.application.validation.ValidationErrorCode;
import com.company.incidentdesk.application.validation.ValidationField;
import com.company.incidentdesk.application.validation.ValidationResult;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentStatus;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

class ReporterPageTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void confirmedSubmissionClearsFieldsAndShowsSuccess() throws Exception {
        ReporterPage page = onFx(() -> new ReporterPage(submission -> ApplicationResult.success(savedIncident())));
        onFx(() -> { fillAndSubmit(page); return null; });

        awaitFeedback(page, "Incident submitted");
        onFx(() -> {
            assertEquals("", title(page).getText());
            assertEquals("", description(page).getText());
            assertEquals(null, category(page).getValue());
            return null;
        });
    }

    @Test
    void unavailableSessionKeepsEnteredValuesAndShowsError() throws Exception {
        ReporterPage page = onFx(() -> new ReporterPage(submission -> ApplicationResult.failure(
                ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE))));
        onFx(() -> { fillAndSubmit(page); return null; });

        awaitFeedback(page, "Submission unavailable");
        assertEnteredValues(page);
    }

    @Test
    void persistenceFailureKeepsEnteredValuesAndShowsError() throws Exception {
        ReporterPage page = onFx(() -> new ReporterPage(submission -> ApplicationResult.failure(
                ApplicationError.of(ApplicationErrorCode.PERSISTENCE_FAILURE))));
        onFx(() -> { fillAndSubmit(page); return null; });

        awaitFeedback(page, "Submission failed");
        assertEnteredValues(page);
    }

    @Test
    void serviceValidationKeepsValuesAndMarksInvalidField() throws Exception {
        ValidationResult validation = ValidationResult.invalid(new ValidationError(
                new ValidationField("title"), ValidationErrorCode.REQUIRED));
        ReporterPage page = onFx(() -> new ReporterPage(submission -> ApplicationResult.failure(
                ApplicationError.validation(validation))));
        onFx(() -> { fillAndSubmit(page); return null; });

        awaitFeedback(page, "Check your report");
        assertEnteredValues(page);
        onFx(() -> {
            assertTrue(title(page).getStyleClass().contains("invalid"));
            return null;
        });
    }

    private static IncidentView savedIncident() {
        return new IncidentView(new IncidentId(UUID.randomUUID()), "Printer failure", "Printer is jammed",
                IncidentCategory.IT, IncidentStatus.SUBMITTED, false, Instant.EPOCH,
                Optional.of(Instant.EPOCH), Optional.empty(), Optional.empty(), 0);
    }

    private static void fillAndSubmit(ReporterPage page) {
        new Scene(page);
        title(page).setText("Printer failure");
        description(page).setText("Printer is jammed");
        category(page).setValue(IncidentCategory.IT);
        ((Button) page.lookup("#submit-incident")).fire();
    }

    private static void assertEnteredValues(ReporterPage page) throws Exception {
        onFx(() -> {
            assertEquals("Printer failure", title(page).getText());
            assertEquals("Printer is jammed", description(page).getText());
            assertEquals(IncidentCategory.IT, category(page).getValue());
            return null;
        });
    }

    private static void awaitFeedback(ReporterPage page, String heading) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (onFx(() -> page.lookupAll(".feedback-card .section-title").stream()
                    .anyMatch(node -> heading.equals(((javafx.scene.control.Label) node).getText())))) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Missing feedback: " + heading);
    }

    private static TextField title(ReporterPage page) {
        return (TextField) page.lookup("#incident-title");
    }

    private static TextArea description(ReporterPage page) {
        return (TextArea) page.lookup("#incident-description");
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<IncidentCategory> category(ReporterPage page) {
        return (ComboBox<IncidentCategory>) page.lookup("#incident-category");
    }

    private static <T> T onFx(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}

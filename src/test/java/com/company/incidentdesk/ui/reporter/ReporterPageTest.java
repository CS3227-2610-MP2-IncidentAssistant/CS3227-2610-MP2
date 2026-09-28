package com.company.incidentdesk.ui.reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.incident.IncidentView;
import com.company.incidentdesk.application.presentation.IncidentActionModel;
import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.presentation.SloSummaryModel;
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
import com.company.incidentdesk.persistence.IncidentSearchCriteria;
import com.company.incidentdesk.ui.shared.components.IncidentTable;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
    void dashboardContentScrollsLikeOtherRolePages() throws Exception {
        ReporterPage page = onFx(ReporterPage::new);

        onFx(() -> {
            ScrollPane scroll = assertInstanceOf(ScrollPane.class, page.getCenter());
            assertTrue(scroll.isFitToWidth());
            assertNotNull(scroll.getContent().lookup("#submit-incident"));
            assertNotNull(scroll.getContent().lookup(".incident-table"));
            return null;
        });
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

    @Test
    void loadsOwnIncidentsAndEnterOpensTheSelectedIncident() throws Exception {
        IncidentView saved = savedIncident();
        IncidentRowModel savedRow = row(saved, "Information Technology", "Submitted");
        AtomicReference<IncidentId> openedIncident = new AtomicReference<>();
        AtomicReference<IncidentSearchCriteria> requestedCriteria = new AtomicReference<>();
        ReporterPage page = onFx(() -> reporterPage(
                submission -> ApplicationResult.success(saved),
                criteria -> {
                    requestedCriteria.set(criteria);
                    return ApplicationResult.success(List.of(savedRow));
                }, openedIncident::set));
        onFx(() -> { new Scene(page); return null; });

        awaitRows(page, 1);
        onFx(() -> {
            IncidentTable incidents = incidentTable(page);
            TableView<IncidentRowModel> table = tableView(incidents);
            assertEquals(savedRow.id(), table.getItems().getFirst().id());
            assertEquals(savedRow.title(), table.getItems().getFirst().title());
            table.getSelectionModel().select(0);
            table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false));
            return null;
        });

        assertEquals(saved.id(), openedIncident.get());
        assertEquals(IncidentSearchCriteria.defaults(), requestedCriteria.get());
    }

    @Test
    void showsEmptyStateWhenReporterHasNoIncidents() throws Exception {
        ReporterPage page = onFx(() -> reporterPage(
                submission -> ApplicationResult.success(savedIncident()),
                criteria -> ApplicationResult.success(List.of()), ignored -> { }));
        onFx(() -> { new Scene(page); return null; });

        awaitPlaceholderText(page, "No incidents found");
    }

    @Test
    void failedIncidentLoadCanBeRetried() throws Exception {
        IncidentView saved = savedIncident();
        IncidentRowModel savedRow = row(saved, "Information Technology", "Submitted");
        AtomicInteger searchCount = new AtomicInteger();
        ReporterPage page = onFx(() -> reporterPage(
                submission -> ApplicationResult.success(saved),
                criteria -> searchCount.incrementAndGet() == 1
                        ? ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE))
                        : ApplicationResult.success(List.of(savedRow)),
                ignored -> { }));
        onFx(() -> { new Scene(page); return null; });

        awaitPlaceholderText(page, "Incidents could not be loaded");
        onFx(() -> {
            Button retry = findButton(tableView(incidentTable(page)).getPlaceholder(), "Try again");
            assertNotNull(retry);
            retry.fire();
            return null;
        });

        awaitRows(page, 1);
        assertEquals(2, searchCount.get());
    }

    @Test
    void successfulSubmissionRefreshesTheIncidentList() throws Exception {
        IncidentView saved = savedIncident();
        IncidentRowModel savedRow = row(saved, "Information Technology", "Submitted");
        AtomicInteger searchCount = new AtomicInteger();
        ReporterPage page = onFx(() -> reporterPage(
                submission -> ApplicationResult.success(saved),
                criteria -> searchCount.incrementAndGet() == 1
                        ? ApplicationResult.success(List.of())
                        : ApplicationResult.success(List.of(savedRow)),
                ignored -> { }));
        onFx(() -> { new Scene(page); return null; });
        awaitPlaceholderText(page, "No incidents found");

        onFx(() -> {
            title(page).setText("Printer failure");
            description(page).setText("Printer is jammed");
            category(page).setValue(IncidentCategory.IT);
            ((Button) content(page).lookup("#submit-incident")).fire();
            return null;
        });

        awaitRows(page, 1);
        assertEquals(2, searchCount.get());
    }

    @Test
    void reattachingReporterPageReloadsItsIncidentList() throws Exception {
        IncidentView original = savedIncident();
        IncidentView refreshed = new IncidentView(
                new IncidentId(UUID.fromString("1462b7a9-c1c0-4c65-8bca-a8cdf36b0f02")),
                "Leaking pipe", "Water is leaking near the server room",
                IncidentCategory.FACILITIES, IncidentStatus.RESOLVED, false, Instant.EPOCH,
                Optional.of(Instant.EPOCH), Optional.empty(), Optional.empty(), 0);
        IncidentRowModel originalRow = row(original, "Information Technology", "Submitted");
        IncidentRowModel refreshedRow = row(refreshed, "Facilities", "Resolved");
        AtomicInteger searchCount = new AtomicInteger();
        ReporterPage page = onFx(() -> reporterPage(
                submission -> ApplicationResult.success(original),
                criteria -> {
                    return ApplicationResult.success(searchCount.incrementAndGet() == 1
                            ? List.of(originalRow) : List.of(refreshedRow));
                }, ignored -> { }));
        Scene scene = onFx(() -> new Scene(page));
        awaitRows(page, 1);

        onFx(() -> {
            scene.setRoot(new javafx.scene.layout.VBox());
            scene.setRoot(page);
            return null;
        });

        awaitRowTitle(page, refreshed.title());
        onFx(() -> {
            IncidentRowModel row = tableView(incidentTable(page)).getItems().getFirst();
            assertEquals(refreshedRow.id(), row.id());
            assertEquals(refreshedRow.title(), row.title());
            return null;
        });
    }

    private static IncidentRowModel row(IncidentView incident, String category, String status) {
        return new IncidentRowModel(
                incident.id(), incident.title(), category, status,
                "You", "Unassigned", "1 Jan 1970, 08:00", "",
                SloSummaryModel.unavailable(), incident.anonymous(), incident.reopenCount(),
                new IncidentActionModel(false, false, false, false, false, false, false, false, false));
    }

    private static IncidentView savedIncident() {
        return new IncidentView(new IncidentId(UUID.fromString("0462b7a9-c1c0-4c65-8bca-a8cdf36b0f01")),
                "Printer failure", "Printer is jammed",
                IncidentCategory.IT, IncidentStatus.SUBMITTED, false, Instant.EPOCH,
                Optional.of(Instant.EPOCH), Optional.empty(), Optional.empty(), 0);
    }

    private static ReporterPage reporterPage(
            Function<IncidentSubmissionForm.Submission, ApplicationResult<IncidentView>> submit,
            Function<IncidentSearchCriteria, ApplicationResult<List<IncidentRowModel>>> search,
            Consumer<IncidentId> onOpenDetail) {
        return new ReporterPage(submit, search, onOpenDetail);
    }

    private static void fillAndSubmit(ReporterPage page) {
        new Scene(page);
        title(page).setText("Printer failure");
        description(page).setText("Printer is jammed");
        category(page).setValue(IncidentCategory.IT);
        ((Button) content(page).lookup("#submit-incident")).fire();
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
            if (onFx(() -> content(page).lookupAll(".feedback-card .section-title").stream()
                    .anyMatch(node -> heading.equals(((javafx.scene.control.Label) node).getText())))) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Missing feedback: " + heading);
    }

    private static void awaitRows(ReporterPage page, int expectedCount) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (onFx(() -> tableView(incidentTable(page)).getItems().size() == expectedCount)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Incident list did not load " + expectedCount + " row(s)");
    }

    private static void awaitRowTitle(ReporterPage page, String expectedTitle) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (onFx(() -> tableView(incidentTable(page)).getItems().stream()
                    .anyMatch(row -> expectedTitle.equals(row.title())))) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Incident list did not show refreshed row: " + expectedTitle);
    }

    private static void awaitPlaceholderText(ReporterPage page, String text) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (onFx(() -> containsLabelText(tableView(incidentTable(page)).getPlaceholder(), text))) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Incident list placeholder did not show: " + text);
    }

    private static boolean containsLabelText(Node node, String text) {
        if (node instanceof Label label && text.equals(label.getText())) {
            return true;
        }
        return node instanceof Parent parent
                && parent.getChildrenUnmodifiable().stream().anyMatch(child -> containsLabelText(child, text));
    }

    private static Button findButton(Node node, String text) {
        if (node instanceof Button button && text.equals(button.getText())) {
            return button;
        }
        if (node instanceof Parent parent) {
            return parent.getChildrenUnmodifiable().stream()
                    .map(child -> findButton(child, text))
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
        }
        return null;
    }

    private static IncidentTable incidentTable(ReporterPage page) {
        return (IncidentTable) content(page).lookup(".incident-table");
    }

    private static Node content(ReporterPage page) {
        return ((ScrollPane) page.getCenter()).getContent();
    }

    @SuppressWarnings("unchecked")
    private static TableView<IncidentRowModel> tableView(IncidentTable incidents) {
        return (TableView<IncidentRowModel>) incidents.lookup(".table-view");
    }

    private static TextField title(ReporterPage page) {
        return (TextField) content(page).lookup("#incident-title");
    }

    private static TextArea description(ReporterPage page) {
        return (TextArea) content(page).lookup("#incident-description");
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<IncidentCategory> category(ReporterPage page) {
        return (ComboBox<IncidentCategory>) content(page).lookup("#incident-category");
    }

    private static <T> T onFx(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}

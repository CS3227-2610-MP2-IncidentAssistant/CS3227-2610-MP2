package com.company.incidentdesk.ui.responder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.company.incidentdesk.application.attachment.AttachmentLimits;
import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.comment.IncidentCommentService;
import com.company.incidentdesk.application.incident.IncidentDetailService;
import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.incident.IncidentView;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.InMemorySessionService;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.attachment.AttachmentId;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.comment.CommentId;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.persistence.AuditQuery;
import com.company.incidentdesk.persistence.AuditSortDirection;
import com.company.incidentdesk.persistence.file.LocalApplicationStore;
import com.company.incidentdesk.ui.shared.components.IncidentDetailState;
import com.company.incidentdesk.ui.shared.components.IncidentDetailView;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import javafx.util.Duration;

class ResponderIncidentPageTest {
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AccountId REPORTER = new AccountId(new UUID(0, 1));
    private static final AccountId RESPONDER = new AccountId(new UUID(0, 2));
    private static final IncidentId INCIDENT = new IncidentId(new UUID(1, 1));
    @TempDir Path directory;
    private LocalApplicationStore store;
    private InMemorySessionService sessions;
    private IncidentService incidents;
    private IncidentDetailService details;
    private IncidentCommentService comments;
    private AttachmentService attachments;
    private IncidentPresentationMapper mapper;
    private StackPane root;
    private ResponderIncidentPage page;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach
    void setUp() {
        store = new LocalApplicationStore(directory);
        store.create(new Account(REPORTER, "private-reporter", Role.REPORTER,
                AccountStatus.ENABLED, ResponderAccess.NONE));
        store.create(new Account(RESPONDER, "responder", Role.RESPONDER,
                AccountStatus.ENABLED, ResponderAccess.to(Set.of(IncidentCategory.IT))));
        store.create(new IncidentLifecycle(CLOCK).submit(INCIDENT, REPORTER,
                "Printer outage", "Paper jam", IncidentCategory.IT, true));
        sessions = new InMemorySessionService(store, (id, password) -> true, CLOCK);
        sessions.login("responder", new char[] {'x'});
        var authorization = new IncidentAuthorizationPolicy(sessions);
        AtomicInteger auditSequence = new AtomicInteger();
        var audits = new AuditEventFactory(CLOCK,
                () -> new AuditEventId(new UUID(2, auditSequence.incrementAndGet())));
        incidents = new IncidentService(sessions, store, store, authorization, new IncidentLifecycle(CLOCK),
                audits, () -> INCIDENT, event -> { });
        comments = new IncidentCommentService(sessions, store, store, authorization, audits, CLOCK,
                () -> new CommentId(new UUID(3, 1)));
        attachments = new AttachmentService(sessions, store, store.attachmentStore(), authorization,
                AttachmentLimits.DEFAULT, audits, CLOCK, () -> new AttachmentId(new UUID(4, 1)));
        mapper = new IncidentPresentationMapper(store, authorization, ZoneOffset.UTC, DateTimeFormatter.ISO_INSTANT);
        details = new IncidentDetailService(sessions, store, authorization, mapper, comments, attachments,
                store.sloConfigurationStore(), CLOCK);
    }

    @AfterEach
    void cleanUp() throws Exception {
        onFx(() -> {
            if (root != null) root.getChildren().clear();
            if (page != null) detail().close();
            return null;
        });
        store.close();
    }

    @Test
    void claimRefreshesDetailAndBothQueuesAndSurvivesRestart() throws Exception {
        ResponderPage dashboard = onFx(() -> new ResponderPage(incidents, mapper, sessions, id -> { }));
        open(incidents::claim, () -> root.getChildren().setAll(dashboard));
        onFx(() -> {
            assertTrue(labels(page).contains("Anonymous reporter"));
            assertFalse(labels(page).contains("private-reporter"));
            button("Claim").fire();
            return null;
        });
        awaitFx(() -> detail().state() instanceof IncidentDetailState.Ready ready
                && !ready.model().summary().actions().claim());
        onFx(() -> {
            assertFalse(button("Resolve").isDisabled());
            assertTrue(labels(page).contains("Incident claimed"));
            button("Back to dashboard").fire();
            return null;
        });
        awaitFx(() -> nodes(dashboard).filter(TableView.class::isInstance)
                .map(TableView.class::cast).mapToInt(table -> table.getItems().size()).sum() == 1);
        onFx(() -> {
            var tables = nodes(dashboard).filter(TableView.class::isInstance).map(TableView.class::cast).toList();
            assertEquals(2, tables.size());
            assertTrue(tables.get(0).getItems().isEmpty());
            assertEquals(1, tables.get(1).getItems().size());
            root.getChildren().clear();
            return null;
        });
        store.close();
        store = new LocalApplicationStore(directory);
        var persisted = store.findById(INCIDENT).orElseThrow();
        assertEquals(IncidentStatus.ASSIGNED, persisted.status());
        assertEquals(RESPONDER, persisted.assigneeId().orElseThrow());
        var audits = store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST);
        assertEquals(1, audits.size());
        assertEquals(AuditAction.INCIDENT_CLAIMED, audits.getFirst().action());
        assertEquals(NOW, audits.getFirst().occurredAt());
        assertFalse(audits.getFirst().changes().isEmpty());
    }

    @Test
    void repeatedClicksSubmitOnlyOnceWhilePending() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        open(id -> {
            assertFalse(Platform.isFxApplicationThread());
            calls.incrementAndGet();
            awaitRelease(release);
            return incidents.claim(id);
        }, () -> { });
        try {
            onFx(() -> {
                Button claim = button("Claim");
                claim.fire();
                claim.fire();
                page.claim(INCIDENT);
                assertTrue(detail().isDisabled());
                assertTrue(labels(page).contains("Claiming incident"));
                return null;
            });
        } finally {
            release.countDown();
        }
        awaitFx(() -> labels(page).contains("Incident claimed"));
        assertEquals(1, calls.get());
        assertEquals(1, store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).size());
    }

    @Test
    void failedSaveShowsSafeFeedbackAndAllowsRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        open(id -> calls.incrementAndGet() == 1
                ? ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.PERSISTENCE_FAILURE))
                : incidents.claim(id), () -> { });
        onFx(() -> { button("Claim").fire(); return null; });
        awaitFx(() -> detail().state() instanceof IncidentDetailState.Ready
                && labels(page).contains("Claim not completed"));
        assertEquals(IncidentStatus.SUBMITTED, store.findById(INCIDENT).orElseThrow().status());
        assertTrue(store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).isEmpty());
        onFx(() -> { assertFalse(button("Claim").isDisabled()); button("Claim").fire(); return null; });
        awaitFx(() -> labels(page).contains("Incident claimed"));
        assertEquals(2, calls.get());
    }

    @Test
    void revokedCategoryClearsDetailAndRejectsStaleClaim() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        open(id -> { calls.incrementAndGet(); return incidents.claim(id); }, () -> { });
        store.update(new Account(RESPONDER, "responder", Role.RESPONDER,
                AccountStatus.ENABLED, ResponderAccess.NONE));
        awaitFx(() -> detail().state() instanceof IncidentDetailState.Unavailable);
        onFx(() -> {
            page.claim(INCIDENT);
            assertFalse(labels(page).contains("Printer outage"));
            return null;
        });
        assertEquals(0, calls.get());
        assertTrue(store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).isEmpty());
    }

    @Test
    void staleAssignmentClearsDetailAndDoesNotSubmitClaim() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        open(id -> { calls.incrementAndGet(); return incidents.claim(id); }, () -> { });
        AccountId other = new AccountId(new UUID(0, 3));
        store.create(new Account(other, "other-responder", Role.RESPONDER, AccountStatus.ENABLED,
                ResponderAccess.to(Set.of(IncidentCategory.IT))));
        store.update(new IncidentLifecycle(CLOCK).claim(store.findById(INCIDENT).orElseThrow(), other));
        onFx(() -> { page.claim(INCIDENT); return null; });
        awaitFx(() -> detail().state() instanceof IncidentDetailState.Unavailable);
        assertEquals(0, calls.get());
        assertEquals(other, store.findById(INCIDENT).orElseThrow().assigneeId().orElseThrow());
        assertTrue(store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).isEmpty());
    }

    @Test
    void leavingPageDiscardsPendingClaimFeedback() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        open(id -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await(10, TimeUnit.SECONDS);
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
            } finally {
                stopped.countDown();
            }
            return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
        }, () -> { });
        onFx(() -> { button("Claim").fire(); return null; });
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        onFx(() -> { root.getChildren().clear(); return null; });
        assertTrue(stopped.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            assertTrue(detail().state() instanceof IncidentDetailState.Unavailable);
            assertFalse(labels(page).contains("Claiming incident"));
            assertFalse(labels(page).contains("Claim not completed"));
            assertFalse(labels(page).contains("Printer outage"));
            return null;
        });
    }

    @Test
    void signedOutPendingClaimCannotRestoreDetailOrMutateIncident() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        open(id -> {
            entered.countDown();
            awaitRelease(release);
            return incidents.claim(id);
        }, () -> { });
        try {
            onFx(() -> { button("Claim").fire(); return null; });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            sessions.logout();
        } finally {
            release.countDown();
        }
        awaitFx(() -> detail().state() instanceof IncidentDetailState.Unavailable && !detail().isDisabled());
        assertEquals(IncidentStatus.SUBMITTED, store.findById(INCIDENT).orElseThrow().status());
        assertTrue(store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).isEmpty());
    }

    private void open(Function<IncidentId, ApplicationResult<IncidentView>> claim, Runnable back) throws Exception {
        open(claim, incidents::resolve, back);
    }

    private void open(Function<IncidentId, ApplicationResult<IncidentView>> claim,
            BiFunction<IncidentId, String, ApplicationResult<IncidentView>> resolve, Runnable back) throws Exception {
        open(claim, resolve, incidents::handoff, back);
    }

    private void open(Function<IncidentId, ApplicationResult<IncidentView>> claim,
            BiFunction<IncidentId, String, ApplicationResult<IncidentView>> resolve,
            Function<IncidentId, ApplicationResult<IncidentView>> handoff, Runnable back) throws Exception {
        onFx(() -> {
            page = new ResponderIncidentPage(claim, resolve, handoff, details, comments, attachments, INCIDENT, back);
            root = new StackPane(page);
            new Scene(root);
            return null;
        });
        awaitFx(() -> detail().state() instanceof IncidentDetailState.Ready);
    }

    private IncidentDetailView detail() { return (IncidentDetailView) page.getCenter(); }

    @Test
    void detailStatePropertyReportsReloadAndAccessLoss() throws Exception {
        open(incidents::claim, () -> { });
        List<IncidentDetailState> observed = new ArrayList<>();
        onFx(() -> {
            assertEquals(detail().state(), detail().stateProperty().get());
            detail().stateProperty().addListener((observable, previous, current) -> {
                assertTrue(Platform.isFxApplicationThread());
                assertEquals(current, detail().state());
                observed.add(current);
            });
            detail().refresh();
            assertTrue(detail().stateProperty().get() instanceof IncidentDetailState.Loading);
            return null;
        });
        awaitFx(() -> detail().stateProperty().get() instanceof IncidentDetailState.Ready);
        sessions.logout();
        awaitFx(() -> detail().stateProperty().get() instanceof IncidentDetailState.Unavailable);
        onFx(() -> {
            assertEquals(3, observed.size());
            assertTrue(observed.get(0) instanceof IncidentDetailState.Loading);
            assertTrue(observed.get(1) instanceof IncidentDetailState.Ready);
            assertTrue(observed.get(2) instanceof IncidentDetailState.Unavailable);
            return null;
        });
    }

    @Test
    void resolutionPersistsEvidenceAndReturnsToRefreshedDashboard() throws Exception {
        assertTrue(incidents.claim(INCIDENT).isSuccess());
        ResponderPage dashboard = onFx(() -> new ResponderPage(incidents, mapper, sessions, id -> { }));
        open(incidents::claim, () -> root.getChildren().setAll(dashboard));
        String remarks = "  Replaced roller\nVerified printing.  ";
        onFx(() -> {
            button("Resolve").fire();
            remarksInput().setText(remarks);
            button("Confirm resolution").fire();
            return null;
        });
        awaitFx(() -> root.getChildren().contains(dashboard)
                && !nodes(dashboard).filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.getText().equals("Refresh")).findFirst().orElseThrow().isDisabled());
        onFx(() -> {
            assertTrue(nodes(dashboard).filter(TableView.class::isInstance).map(TableView.class::cast)
                    .allMatch(table -> table.getItems().isEmpty()));
            assertTrue(detail().state() instanceof IncidentDetailState.Unavailable);
            assertTrue(page.getBottom() == null);
            root.getChildren().clear();
            return null;
        });
        store.close();
        store = new LocalApplicationStore(directory);
        var saved = store.findById(INCIDENT).orElseThrow();
        assertEquals(IncidentStatus.RESOLVED, saved.status());
        assertTrue(saved.assigneeId().isEmpty());
        var cycle = saved.currentCycle().orElseThrow();
        var resolution = cycle.resolution().orElseThrow();
        assertEquals(remarks, resolution.remarks());
        assertEquals(RESPONDER, resolution.resolvedBy());
        assertEquals(RESPONDER, resolution.responderAtResolution());
        assertEquals(NOW, resolution.resolvedAt());
        assertEquals(NOW, cycle.firstAssignedAt().orElseThrow());
        var audits = store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST);
        assertEquals(2, audits.size());
        assertEquals(AuditAction.INCIDENT_RESOLVED, audits.getLast().action());
        assertTrue(audits.getLast().evidenceReference().isPresent());
        assertFalse(audits.toString().contains(remarks));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t\n "})
    void blankResolutionShowsFieldFeedbackWithoutDiscardingInput(String remarks) throws Exception {
        assertTrue(incidents.claim(INCIDENT).isSuccess());
        open(incidents::claim, () -> { });
        onFx(() -> {
            button("Resolve").fire();
            remarksInput().setText(remarks);
            button("Confirm resolution").fire();
            return null;
        });
        awaitFx(() -> labels(page).contains("Resolution remarks are required."));
        onFx(() -> {
            assertEquals(remarks, remarksInput().getText());
            assertFalse(button("Confirm resolution").isDisabled());
            assertTrue(remarksInput().getAccessibleText().contains("required"));
            return null;
        });
        assertEquals(IncidentStatus.ASSIGNED, store.findById(INCIDENT).orElseThrow().status());
        assertEquals(1, store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).size());
    }

    @Test
    void failedResolutionPreservesRemarksAndCanBeRetried() throws Exception {
        assertTrue(incidents.claim(INCIDENT).isSuccess());
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger returned = new AtomicInteger();
        open(incidents::claim, (id, remarks) -> calls.incrementAndGet() == 1
                ? ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.PERSISTENCE_FAILURE))
                : incidents.resolve(id, remarks), returned::incrementAndGet);
        onFx(() -> {
            button("Resolve").fire();
            remarksInput().setText(" Repaired ");
            button("Confirm resolution").fire();
            return null;
        });
        awaitFx(() -> labels(page).contains("Resolution not saved"));
        assertEquals(1, store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).size());
        onFx(() -> {
            assertEquals(" Repaired ", remarksInput().getText());
            button("Confirm resolution").fire();
            return null;
        });
        awaitFx(() -> returned.get() == 1);
        assertEquals(2, calls.get());
    }

    @Test
    void pendingResolutionRejectsRepeatClicksAndDirectSubmissions() throws Exception {
        assertTrue(incidents.claim(INCIDENT).isSuccess());
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger returned = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        open(incidents::claim, (id, remarks) -> {
            assertFalse(Platform.isFxApplicationThread());
            calls.incrementAndGet();
            awaitRelease(release);
            return incidents.resolve(id, remarks);
        }, returned::incrementAndGet);
        try {
            onFx(() -> {
                button("Resolve").fire();
                remarksInput().setText("Done");
                Button submit = button("Confirm resolution");
                submit.fire();
                submit.fire();
                page.resolve("Duplicate");
                assertTrue(submit.isDisabled());
                assertTrue(button("Cancel").isDisabled());
                assertTrue(remarksInput().isDisabled());
                return null;
            });
        } finally {
            release.countDown();
        }
        awaitFx(() -> returned.get() == 1);
        assertEquals(1, calls.get());
        assertEquals(2, store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"revoke", "reassign", "logout"})
    void staleResolutionFormIsClearedAndCannotCommit(String change) throws Exception {
        assertTrue(incidents.claim(INCIDENT).isSuccess());
        AtomicInteger calls = new AtomicInteger();
        open(incidents::claim, (id, remarks) -> {
            calls.incrementAndGet();
            return incidents.resolve(id, remarks);
        }, () -> { });
        TextArea input = onFx(() -> {
            button("Resolve").fire();
            remarksInput().setText("Private work notes");
            return remarksInput();
        });
        switch (change) {
        case "revoke" -> store.update(new Account(RESPONDER, "responder", Role.RESPONDER,
                AccountStatus.ENABLED, ResponderAccess.NONE));
        case "reassign" -> {
            AccountId other = new AccountId(new UUID(0, 3));
            store.create(new Account(other, "other", Role.RESPONDER, AccountStatus.ENABLED,
                    ResponderAccess.to(Set.of(IncidentCategory.IT))));
            store.update(new IncidentLifecycle(CLOCK).reassign(store.findById(INCIDENT).orElseThrow(), other));
        }
        case "logout" -> sessions.logout();
        default -> throw new AssertionError("Unknown test change");
        }
        awaitFx(() -> detail().state() instanceof IncidentDetailState.Unavailable && page.getBottom() == null);
        onFx(() -> {
            assertEquals("", input.getText());
            page.resolve("Stale submission");
            return null;
        });
        assertEquals(0, calls.get());
        assertEquals(IncidentStatus.ASSIGNED, store.findById(INCIDENT).orElseThrow().status());
        assertEquals(1, store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).size());
    }

    private TextArea remarksInput() {
        return nodes(page).filter(TextArea.class::isInstance).map(TextArea.class::cast)
                .filter(input -> "resolution-remarks".equals(input.getId())).findFirst().orElseThrow();
    }

    @Test
    void cancelClearsRemarksWithoutResolvingAndAllowsReopeningForm() throws Exception {
        assertTrue(incidents.claim(INCIDENT).isSuccess());
        open(incidents::claim, () -> { });
        onFx(() -> {
            button("Resolve").fire();
            TextArea input = remarksInput();
            input.setText("Unfinished notes");
            button("Cancel").fire();
            assertEquals("", input.getText());
            assertTrue(page.getBottom() == null);
            button("Resolve").fire();
            assertEquals("", remarksInput().getText());
            return null;
        });
        assertEquals(IncidentStatus.ASSIGNED, store.findById(INCIDENT).orElseThrow().status());
        assertEquals(1, store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).size());
    }

    @Test
    void logoutDuringPendingResolutionDiscardsInputAndCannotNavigateOrCommit() throws Exception {
        assertTrue(incidents.claim(INCIDENT).isSuccess());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger returned = new AtomicInteger();
        open(incidents::claim, (id, remarks) -> {
            entered.countDown();
            awaitRelease(release);
            return incidents.resolve(id, remarks);
        }, returned::incrementAndGet);
        try {
            onFx(() -> {
                button("Resolve").fire();
                remarksInput().setText("Done");
                button("Confirm resolution").fire();
                return null;
            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            sessions.logout();
        } finally {
            release.countDown();
        }
        awaitFx(() -> detail().state() instanceof IncidentDetailState.Unavailable && !detail().isDisabled());
        assertEquals(0, returned.get());
        assertEquals(IncidentStatus.ASSIGNED, store.findById(INCIDENT).orElseThrow().status());
        assertEquals(1, store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).size());
    }

    @Test
    void handsOffAssignedIncidentBackToTheQueueAfterConfirmation() throws Exception {
        assertTrue(incidents.claim(INCIDENT).isSuccess());
        open(incidents::claim, () -> { });
        Platform.runLater(() -> button("Hand off").fire());
        Window confirmation = waitForWindowWithLookup("#handoff-confirmation");
        Platform.runLater(() -> clickButtonByText(confirmation, "Hand off"));
        awaitFx(() -> labels(page).contains("Incident handed off"));
        var persisted = store.findById(INCIDENT).orElseThrow();
        assertEquals(IncidentStatus.SUBMITTED, persisted.status());
        assertTrue(persisted.assigneeId().isEmpty());
        var audits = store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST);
        assertEquals(2, audits.size());
        assertEquals(AuditAction.INCIDENT_HANDED_OFF, audits.getLast().action());
    }

    @Test
    void cancellingHandoffConfirmationLeavesIncidentAssigned() throws Exception {
        assertTrue(incidents.claim(INCIDENT).isSuccess());
        open(incidents::claim, () -> { });
        Platform.runLater(() -> button("Hand off").fire());
        Window confirmation = waitForWindowWithLookup("#handoff-confirmation");
        onFx(() -> {
            clickButtonByText(confirmation, "Cancel");
            return null;
        });
        onFx(() -> {
            assertFalse(labels(page).contains("Incident handed off"));
            return null;
        });
        assertEquals(IncidentStatus.ASSIGNED, store.findById(INCIDENT).orElseThrow().status());
        assertEquals(1, store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).size());
    }

    @Test
    void repeatedHandoffClicksSubmitOnlyOnce() throws Exception {
        assertTrue(incidents.claim(INCIDENT).isSuccess());
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        open(incidents::claim, incidents::resolve, id -> {
            calls.incrementAndGet();
            awaitRelease(release);
            return incidents.handoff(id);
        }, () -> { });
        Platform.runLater(() -> button("Hand off").fire());
        Window confirmation = waitForWindowWithLookup("#handoff-confirmation");
        Platform.runLater(() -> clickButtonByText(confirmation, "Hand off"));
        awaitFx(() -> detail().isDisabled());
        try {
            onFx(() -> {
                button("Hand off").fire();
                page.handoff(INCIDENT);
                assertTrue(detail().isDisabled());
                return null;
            });
        } finally {
            release.countDown();
        }
        awaitFx(() -> labels(page).contains("Incident handed off"));
        assertEquals(1, calls.get());
    }

    private static Window waitForWindowWithLookup(String selector) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            Optional<Window> found = onFx(() -> Window.getWindows().stream()
                    .filter(Window::isShowing)
                    .filter(window -> window.getScene() != null && window.getScene().lookup(selector) != null)
                    .findFirst());
            if (found.isPresent()) {
                return found.orElseThrow();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("No showing window matched " + selector + " within timeout");
    }

    private static void clickButtonByText(Window window, String text) {
        ((Button) window.getScene().getRoot().lookupAll(".button").stream()
                .filter(node -> node instanceof Button button && text.equals(button.getText()))
                .findFirst().orElseThrow()).fire();
    }

    private Button button(String text) {
        return nodes(page).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals(text)).findFirst().orElseThrow();
    }

    private static List<String> labels(Node node) {
        return nodes(node).filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList();
    }

    private static Stream<Node> nodes(Node node) {
        if (node instanceof ScrollPane scroll) return Stream.concat(Stream.of(node), nodes(scroll.getContent()));
        if (node instanceof Parent parent) return Stream.concat(Stream.of(node),
                parent.getChildrenUnmodifiable().stream().flatMap(ResponderIncidentPageTest::nodes));
        return Stream.of(node);
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            assertTrue(release.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static void awaitFx(BooleanSupplier condition) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Timeline timer = onFx(() -> {
            Timeline poll = new Timeline(new KeyFrame(Duration.millis(20), event -> {
                if (condition.getAsBoolean()) ready.countDown();
            }));
            poll.setCycleCount(Timeline.INDEFINITE);
            poll.play();
            return poll;
        });
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS), "UI condition was not reached");
        } finally {
            onFx(() -> { timer.stop(); return null; });
        }
    }

    private static <T> T onFx(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}

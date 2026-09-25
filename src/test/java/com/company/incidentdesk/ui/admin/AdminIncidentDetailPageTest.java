package com.company.incidentdesk.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.company.incidentdesk.application.account.AccountDirectoryService;
import com.company.incidentdesk.application.attachment.AttachmentLimits;
import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.comment.IncidentCommentService;
import com.company.incidentdesk.application.incident.IncidentDetailService;
import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.incident.IncidentView;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import javafx.util.Duration;

class AdminIncidentDetailPageTest {
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AccountId REPORTER = new AccountId(new UUID(0, 1));
    private static final AccountId RESPONDER = new AccountId(new UUID(0, 2));
    private static final AccountId OTHER_RESPONDER = new AccountId(new UUID(0, 3));
    private static final IncidentId INCIDENT = new IncidentId(new UUID(1, 1));
    @TempDir Path directory;
    private LocalApplicationStore store;
    private InMemorySessionService sessions;
    private IncidentService incidents;
    private IncidentDetailService details;
    private IncidentCommentService comments;
    private AttachmentService attachments;
    private AccountDirectoryService accountDirectory;
    private StackPane root;
    private AdminIncidentDetailPage page;

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
        store.create(new Account(OTHER_RESPONDER, "other-responder", Role.RESPONDER,
                AccountStatus.ENABLED, ResponderAccess.to(Set.of(IncidentCategory.FACILITIES))));
        store.create(new Account(new AccountId(new UUID(0, 4)), "admin", Role.ADMINISTRATOR,
                AccountStatus.ENABLED, ResponderAccess.NONE));
        store.create(new IncidentLifecycle(CLOCK).submit(INCIDENT, REPORTER,
                "Printer outage", "Paper jam", IncidentCategory.IT, true));
        sessions = new InMemorySessionService(store, (id, password) -> true, CLOCK);
        sessions.login("admin", new char[] {'x'});
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
        var mapper = new IncidentPresentationMapper(store, authorization, ZoneOffset.UTC, DateTimeFormatter.ISO_INSTANT);
        details = new IncidentDetailService(sessions, store, authorization, mapper, comments, attachments,
                store.sloConfigurationStore(), CLOCK);
        accountDirectory = new AccountDirectoryService(new AccountAuthorizationPolicy(sessions), store);
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
    void reassignsUnassignedSubmittedIncidentToEligibleResponder() throws Exception {
        open(incidents::reassign, incidents::resolve, accountDirectory::listAccounts, () -> { });
        onFx(() -> {
            assertTrue(labels(page).contains("Anonymous reporter"));
            button("Assign").fire();
            return null;
        });
        awaitFx(() -> responderCombo() != null);
        onFx(() -> {
            List<?> items = responderCombo().getItems();
            assertEquals(1, items.size());
            responderCombo().setValue(items.get(0));
            button("Confirm reassignment").fire();
            return null;
        });
        awaitFx(() -> detail().state() instanceof IncidentDetailState.Ready ready
                && ready.model().summary().actions().resolve());
        onFx(() -> {
            assertTrue(labels(page).contains("Incident reassigned"));
            return null;
        });
        var persisted = store.findById(INCIDENT).orElseThrow();
        assertEquals(IncidentStatus.ASSIGNED, persisted.status());
        assertEquals(RESPONDER, persisted.assigneeId().orElseThrow());
        var audits = store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST);
        assertEquals(1, audits.size());
        assertEquals(AuditAction.INCIDENT_REASSIGNED, audits.getFirst().action());
    }

    @Test
    void reassignmentPickerOffersOnlyEligibleEnabledResponders() throws Exception {
        open(incidents::reassign, incidents::resolve, accountDirectory::listAccounts, () -> { });
        onFx(() -> { button("Assign").fire(); return null; });
        awaitFx(() -> responderCombo() != null);
        onFx(() -> {
            List<String> names = responderCombo().getItems().stream().map(Object::toString).toList();
            assertEquals(List.of("responder"), names);
            return null;
        });
    }

    @Test
    void noEligibleResponderShowsFeedbackAndDoesNotOpenForm() throws Exception {
        store.update(new Account(RESPONDER, "responder", Role.RESPONDER,
                AccountStatus.ENABLED, ResponderAccess.NONE));
        open(incidents::reassign, incidents::resolve, accountDirectory::listAccounts, () -> { });
        onFx(() -> { button("Assign").fire(); return null; });
        awaitFx(() -> labels(page).contains("No eligible responders"));
        onFx(() -> {
            assertNull(page.getBottom());
            return null;
        });
    }

    @Test
    void resolvesAssignedIncidentAndStaysOnDetailPage() throws Exception {
        assertTrue(incidents.reassign(INCIDENT, RESPONDER).isSuccess());
        open(incidents::reassign, incidents::resolve, accountDirectory::listAccounts, () -> { });
        onFx(() -> {
            button("Resolve").fire();
            remarksInput().setText("  Replaced roller  ");
            button("Confirm resolution").fire();
            return null;
        });
        awaitFx(() -> labels(page).contains("Incident resolved"));
        var persisted = store.findById(INCIDENT).orElseThrow();
        assertEquals(IncidentStatus.RESOLVED, persisted.status());
        assertEquals("  Replaced roller  ",
                persisted.currentCycle().orElseThrow().resolution().orElseThrow().remarks());
        assertTrue(root.getChildren().contains(page));
    }

    @Test
    void blankResolutionShowsFieldErrorWithoutDiscardingInput() throws Exception {
        assertTrue(incidents.reassign(INCIDENT, RESPONDER).isSuccess());
        open(incidents::reassign, incidents::resolve, accountDirectory::listAccounts, () -> { });
        onFx(() -> {
            button("Resolve").fire();
            remarksInput().setText(" \n ");
            button("Confirm resolution").fire();
            return null;
        });
        awaitFx(() -> labels(page).contains("Resolution remarks are required."));
        onFx(() -> {
            assertEquals(" \n ", remarksInput().getText());
            return null;
        });
        assertEquals(IncidentStatus.ASSIGNED, store.findById(INCIDENT).orElseThrow().status());
    }

    @Test
    void unassignsAssignedIncidentBackToTheQueueAfterConfirmation() throws Exception {
        assertTrue(incidents.reassign(INCIDENT, RESPONDER).isSuccess());
        open(incidents::reassign, incidents::resolve, incidents::handoff, accountDirectory::listAccounts, () -> { });
        Platform.runLater(() -> button("Hand off").fire());
        Window confirmation = waitForWindowWithLookup("#unassign-confirmation");
        Platform.runLater(() -> clickButtonByText(confirmation, "Unassign"));
        awaitFx(() -> labels(page).contains("Incident unassigned"));
        var persisted = store.findById(INCIDENT).orElseThrow();
        assertEquals(IncidentStatus.SUBMITTED, persisted.status());
        assertTrue(persisted.assigneeId().isEmpty());
        var audits = store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST);
        assertEquals(2, audits.size());
        assertEquals(AuditAction.INCIDENT_HANDED_OFF, audits.getLast().action());
    }

    @Test
    void cancellingUnassignConfirmationLeavesIncidentAssigned() throws Exception {
        assertTrue(incidents.reassign(INCIDENT, RESPONDER).isSuccess());
        open(incidents::reassign, incidents::resolve, incidents::handoff, accountDirectory::listAccounts, () -> { });
        Platform.runLater(() -> button("Hand off").fire());
        Window confirmation = waitForWindowWithLookup("#unassign-confirmation");
        onFx(() -> {
            clickButtonByText(confirmation, "Cancel");
            return null;
        });
        onFx(() -> {
            assertFalse(labels(page).contains("Incident unassigned"));
            return null;
        });
        assertEquals(IncidentStatus.ASSIGNED, store.findById(INCIDENT).orElseThrow().status());
        assertEquals(1, store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).size());
    }

    @Test
    void handOffButtonIsNotOfferedForAnUnassignedIncident() throws Exception {
        open(incidents::reassign, incidents::resolve, accountDirectory::listAccounts, () -> { });
        onFx(() -> {
            assertTrue(nodes(page).filter(Button.class::isInstance).map(Button.class::cast)
                    .noneMatch(button -> button.getText().equals("Hand off")));
            return null;
        });
    }

    @Test
    void repeatedUnassignClicksSubmitOnlyOnce() throws Exception {
        assertTrue(incidents.reassign(INCIDENT, RESPONDER).isSuccess());
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        Function<IncidentId, ApplicationResult<IncidentView>> slowHandoff = id -> {
            calls.incrementAndGet();
            awaitRelease(release);
            return incidents.handoff(id);
        };
        open(incidents::reassign, incidents::resolve, slowHandoff, accountDirectory::listAccounts, () -> { });
        Platform.runLater(() -> button("Hand off").fire());
        Window confirmation = waitForWindowWithLookup("#unassign-confirmation");
        Platform.runLater(() -> clickButtonByText(confirmation, "Unassign"));
        awaitFx(() -> detail().isDisabled());
        try {
            onFx(() -> {
                button("Hand off").fire();
                page.unassign(INCIDENT);
                assertTrue(detail().isDisabled());
                return null;
            });
        } finally {
            release.countDown();
        }
        awaitFx(() -> labels(page).contains("Incident unassigned"));
        assertEquals(1, calls.get());
    }

    @Test
    void ineligibleReassignmentIsRejectedAndFormDiscarded() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        BiFunction<IncidentId, AccountId, ApplicationResult<IncidentView>> staleReassign = (id, responderId) -> {
            calls.incrementAndGet();
            store.update(new Account(RESPONDER, "responder", Role.RESPONDER,
                    AccountStatus.ENABLED, ResponderAccess.NONE));
            return incidents.reassign(id, responderId);
        };
        open(staleReassign, incidents::resolve, accountDirectory::listAccounts, () -> { });
        onFx(() -> { button("Assign").fire(); return null; });
        awaitFx(() -> responderCombo() != null);
        onFx(() -> {
            responderCombo().setValue(responderCombo().getItems().get(0));
            button("Confirm reassignment").fire();
            return null;
        });
        awaitFx(() -> labels(page).contains("Reassignment not completed"));
        assertEquals(1, calls.get());
        onFx(() -> {
            assertNull(page.getBottom());
            return null;
        });
        assertTrue(store.findById(INCIDENT).orElseThrow().assigneeId().isEmpty());
    }

    @Test
    void repeatedReassignmentClicksSubmitOnlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        BiFunction<IncidentId, AccountId, ApplicationResult<IncidentView>> slowReassign = (id, responderId) -> {
            calls.incrementAndGet();
            awaitRelease(release);
            return incidents.reassign(id, responderId);
        };
        open(slowReassign, incidents::resolve, accountDirectory::listAccounts, () -> { });
        onFx(() -> { button("Assign").fire(); return null; });
        awaitFx(() -> responderCombo() != null);
        try {
            onFx(() -> {
                responderCombo().setValue(responderCombo().getItems().get(0));
                Button submit = button("Confirm reassignment");
                submit.fire();
                submit.fire();
                page.reassign(RESPONDER);
                assertTrue(detail().isDisabled());
                return null;
            });
        } finally {
            release.countDown();
        }
        awaitFx(() -> labels(page).contains("Incident reassigned"));
        assertEquals(1, calls.get());
    }

    @Test
    void cancelReassignmentDiscardsFormWithoutSubmitting() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        open((id, responderId) -> { calls.incrementAndGet(); return incidents.reassign(id, responderId); },
                incidents::resolve, accountDirectory::listAccounts, () -> { });
        onFx(() -> { button("Assign").fire(); return null; });
        awaitFx(() -> responderCombo() != null);
        onFx(() -> {
            button("Cancel").fire();
            assertNull(page.getBottom());
            return null;
        });
        assertEquals(0, calls.get());
        assertTrue(store.findById(INCIDENT).orElseThrow().assigneeId().isEmpty());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ComboBox<Object> responderCombo() {
        return nodes(page).filter(ComboBox.class::isInstance).map(box -> (ComboBox<Object>) box)
                .filter(box -> "reassignment-responder".equals(box.getId())).findFirst().orElse(null);
    }

    private TextArea remarksInput() {
        return nodes(page).filter(TextArea.class::isInstance).map(TextArea.class::cast)
                .filter(input -> "resolution-remarks".equals(input.getId())).findFirst().orElseThrow();
    }

    private void open(
            BiFunction<IncidentId, AccountId, ApplicationResult<IncidentView>> reassign,
            BiFunction<IncidentId, String, ApplicationResult<IncidentView>> resolve,
            Supplier<ApplicationResult<List<Account>>> eligibleResponders,
            Runnable back) throws Exception {
        open(reassign, resolve, incidents::handoff, eligibleResponders, back);
    }

    private void open(
            BiFunction<IncidentId, AccountId, ApplicationResult<IncidentView>> reassign,
            BiFunction<IncidentId, String, ApplicationResult<IncidentView>> resolve,
            Function<IncidentId, ApplicationResult<IncidentView>> handoff,
            Supplier<ApplicationResult<List<Account>>> eligibleResponders,
            Runnable back) throws Exception {
        Function<IncidentId, ApplicationResult<IncidentView>> detailOperation = incidents::detail;
        onFx(() -> {
            page = new AdminIncidentDetailPage(detailOperation, resolve, reassign, handoff, eligibleResponders,
                    details, comments, attachments, INCIDENT, back);
            root = new StackPane(page);
            new Scene(root);
            return null;
        });
        awaitFx(() -> detail().state() instanceof IncidentDetailState.Ready);
    }

    private IncidentDetailView detail() { return (IncidentDetailView) page.getCenter(); }

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
                parent.getChildrenUnmodifiable().stream().flatMap(AdminIncidentDetailPageTest::nodes));
        return Stream.of(node);
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

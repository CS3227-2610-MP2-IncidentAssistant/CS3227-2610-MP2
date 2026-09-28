package com.company.incidentdesk.ui.reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.company.incidentdesk.application.attachment.AttachmentLimits;
import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.comment.IncidentCommentService;
import com.company.incidentdesk.application.incident.IncidentDetailService;
import com.company.incidentdesk.application.incident.IncidentService;
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
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.comment.CommentId;
import com.company.incidentdesk.domain.comment.CommentType;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.persistence.file.LocalApplicationStore;
import com.company.incidentdesk.ui.shared.components.AttachmentPane;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import javafx.util.Duration;

class ReporterIncidentPageTest {
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AccountId REPORTER = new AccountId(new UUID(0, 1));
    private static final AccountId RESPONDER = new AccountId(new UUID(0, 2));
    private static final IncidentId INCIDENT = new IncidentId(new UUID(1, 1));

    @TempDir Path directory;
    private LocalApplicationStore store;
    private IncidentService incidents;
    private IncidentDetailService details;
    private IncidentCommentService comments;
    private AttachmentService attachments;
    private StackPane root;
    private ReporterIncidentPage page;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void setUp() {
        store = new LocalApplicationStore(directory.resolve("store"));
        store.create(new Account(REPORTER, "reporter", Role.REPORTER,
                AccountStatus.ENABLED, ResponderAccess.NONE));
        store.create(new Account(RESPONDER, "responder", Role.RESPONDER,
                AccountStatus.ENABLED, ResponderAccess.to(Set.of(IncidentCategory.IT))));
        store.create(new IncidentLifecycle(CLOCK).submit(INCIDENT, REPORTER,
                "Printer outage", "Paper jam", IncidentCategory.IT, true));
        var sessions = new InMemorySessionService(store, (id, password) -> true, CLOCK);
        sessions.login("reporter", new char[] {'x'});
        var authorization = new IncidentAuthorizationPolicy(sessions);
        var audits = new AuditEventFactory(CLOCK, () -> new AuditEventId(UUID.randomUUID()));
        incidents = new IncidentService(sessions, store, store, authorization, new IncidentLifecycle(CLOCK),
                audits, () -> INCIDENT, event -> { });
        comments = new IncidentCommentService(sessions, store, store, authorization, audits, CLOCK,
                () -> new CommentId(UUID.randomUUID()));
        attachments = new AttachmentService(sessions, store, store.attachmentStore(), authorization,
                AttachmentLimits.DEFAULT, audits, CLOCK, () -> new AttachmentId(UUID.randomUUID()));
        var mapper = new IncidentPresentationMapper(
                store, authorization, ZoneOffset.UTC, DateTimeFormatter.ISO_INSTANT);
        details = new IncidentDetailService(sessions, store, authorization, mapper, comments, attachments,
                store.sloConfigurationStore(), CLOCK);
    }

    @AfterEach
    void cleanUp() throws Exception {
        onFx(() -> {
            if (root != null) {
                root.getChildren().clear();
            }
            return null;
        });
        store.close();
    }

    @Test
    void editsOwnedUnassignedIncidentAndKeepsItsAnonymousSetting() throws Exception {
        open();
        onFx(() -> {
            button("Edit").fire();
            assertEquals("Printer outage", editTitle().getText());
            assertEquals("Paper jam", editDescription().getText());
            assertEquals(IncidentCategory.IT, editCategory().getValue());
            editTitle().setText("Printer unavailable");
            editDescription().setText("The printer still jams after restart.");
            editCategory().setValue(IncidentCategory.FACILITIES);
            button("Save changes").fire();
            return null;
        });

        awaitFx(() -> detail().state() instanceof IncidentDetailState.Ready ready
                && ready.model().summary().title().equals("Printer unavailable"));
        var saved = store.findById(INCIDENT).orElseThrow();
        assertEquals("The printer still jams after restart.", saved.description());
        assertEquals(IncidentCategory.FACILITIES, saved.category());
        assertTrue(saved.anonymous());
        onFx(() -> {
            assertNullForm();
            assertTrue(labels(page).contains("Incident updated"));
            return null;
        });
    }

    @Test
    void editValidationKeepsFormValuesAndDoesNotChangeIncident() throws Exception {
        open();
        onFx(() -> {
            button("Edit").fire();
            editTitle().clear();
            editDescription().setText("Keep this description in the form.");
            button("Save changes").fire();
            assertEquals("", editTitle().getText());
            assertEquals("Keep this description in the form.", editDescription().getText());
            return null;
        });

        awaitFx(() -> labels(page).contains("Title is required."));
        assertEquals("Printer outage", store.findById(INCIDENT).orElseThrow().title());
    }

    @Test
    void editRejectedAfterAssignmentRefreshesDetailsAndPreservesValues() throws Exception {
        open();
        onFx(() -> {
            button("Edit").fire();
            editTitle().setText("Changes made before assignment");
            editDescription().setText("Keep this text for reference.");
            return null;
        });
        store.update(new IncidentLifecycle(CLOCK).claim(store.findById(INCIDENT).orElseThrow(), RESPONDER));
        onFx(() -> {
            button("Save changes").fire();
            return null;
        });

        awaitFx(() -> detail().state() instanceof IncidentDetailState.Ready ready
                && ready.model().summary().statusLabel().equals("Assigned")
                && !editTitle().isEditable());
        onFx(() -> {
            assertEquals("Changes made before assignment", editTitle().getText());
            assertEquals("Keep this text for reference.", editDescription().getText());
            assertTrue(button("Save changes").isDisabled());
            assertFalse(button("Cancel").isDisabled());
            return null;
        });
    }

    @Test
    void reopensResolvedIncidentWithFollowUpAndKeepsResolutionHistory() throws Exception {
        var lifecycle = new IncidentLifecycle(CLOCK);
        var resolved = lifecycle.resolve(
                lifecycle.claim(lifecycle.submit(INCIDENT, REPORTER, "Printer outage", "Paper jam",
                        IncidentCategory.IT, false), RESPONDER),
                RESPONDER, "Replaced the printer cartridge.");
        store.update(resolved);
        open();
        onFx(() -> {
            button("Reopen").fire();
            button("Submit follow-up").fire();
            assertEquals("", followUp().getText());
            followUp().setText("The printer still jams with the new cartridge.");
            button("Submit follow-up").fire();
            return null;
        });

        awaitFx(() -> detail().state() instanceof IncidentDetailState.Ready ready
                && ready.model().summary().statusLabel().equals("Submitted"));
        var saved = store.findById(INCIDENT).orElseThrow();
        assertEquals(IncidentStatus.SUBMITTED, saved.status());
        assertEquals(2, saved.resolutionCycles().size());
        var savedResolution = saved.resolutionCycles().stream()
                .filter(cycle -> cycle.resolution().isPresent())
                .findFirst().orElseThrow().resolution().orElseThrow();
        assertEquals("Replaced the printer cartridge.", savedResolution.remarks());
        var savedComments = store.findCommentsByIncidentId(INCIDENT);
        assertEquals(CommentType.REOPEN_EXPLANATION, savedComments.getFirst().type());
        assertEquals("The printer still jams with the new cartridge.", savedComments.getFirst().text());
        onFx(() -> {
            assertTrue(labels(page).contains("Incident reopened"));
            assertNullForm();
            return null;
        });
    }

    @Test
    void followUpRejectedAfterAnotherReopenRefreshesDetailsAndPreservesExplanation() throws Exception {
        var lifecycle = new IncidentLifecycle(CLOCK);
        store.update(lifecycle.resolve(
                lifecycle.claim(store.findById(INCIDENT).orElseThrow(), RESPONDER),
                RESPONDER, "Replaced the printer cartridge."));
        open();
        onFx(() -> {
            button("Reopen").fire();
            followUp().setText("The printer still jams.");
            return null;
        });
        assertTrue(incidents.reopen(INCIDENT, "A second report reopened this incident.").isSuccess());
        onFx(() -> {
            button("Submit follow-up").fire();
            return null;
        });

        awaitFx(() -> detail().state() instanceof IncidentDetailState.Ready ready
                && ready.model().summary().statusLabel().equals("Submitted")
                && !followUp().isEditable());
        onFx(() -> {
            assertEquals("The printer still jams.", followUp().getText());
            assertTrue(button("Submit follow-up").isDisabled());
            assertFalse(button("Cancel").isDisabled());
            return null;
        });
    }

    @Test
    void reporterCanUploadAndViewAnImageOnAnEditableIncident() throws Exception {
        open();
        assertTrue(attachments.canAdd(INCIDENT));
        assertTrue(attachments.list(INCIDENT).isSuccess());
        // The off-screen test scene does not show a Stage to attach the nested ScrollPane content.
        onFx(() -> {
            attachmentPane().refresh();
            return null;
        });
        awaitFx(() -> !button("Add attachment").isDisabled());
        Path image = directory.resolve("evidence.png");
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", image.toFile());
        onFx(() -> {
            assertFalse(button("Add attachment").isDisabled());
            attachmentPane().addFile(image);
            return null;
        });

        awaitFx(() -> store.attachmentStore().list(INCIDENT).size() == 1);
        awaitFx(() -> labels(attachmentPane()).contains("Image.png"));
        onFx(() -> {
            assertFalse(labels(attachmentPane()).contains("evidence.png"));
            assertNotNull(button("View attachment"));
            return null;
        });
    }

    @Test
    void withdrawRequiresConfirmationAndThenRefreshesTheDetail() throws Exception {
        open();
        Platform.runLater(() -> button("Withdraw").fire());
        Window confirmation = waitForWindowWithLookup("#withdraw-confirmation");
        Platform.runLater(() -> clickButtonByText(confirmation, "Withdraw"));

        awaitFx(() -> detail().state() instanceof IncidentDetailState.Ready ready
                && ready.model().summary().statusLabel().equals("Withdrawn"));
        assertEquals(IncidentStatus.WITHDRAWN, store.findById(INCIDENT).orElseThrow().status());
        onFx(() -> {
            assertTrue(labels(page).contains("Incident withdrawn"));
            return null;
        });
    }

    private void open() throws Exception {
        onFx(() -> {
            page = new ReporterIncidentPage(incidents, details, comments, attachments, INCIDENT, () -> { });
            root = new StackPane(page);
            new Scene(root);
            return null;
        });
        awaitFx(() -> detail().state() instanceof IncidentDetailState.Ready);
    }

    private IncidentDetailView detail() {
        return (IncidentDetailView) page.getCenter();
    }

    private Button button(String text) {
        return nodes(page).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getText().equals(text)).findFirst().orElseThrow();
    }

    private TextField editTitle() {
        return (TextField) nodes(page).filter(TextField.class::isInstance).map(TextField.class::cast)
                .filter(field -> field.getId().equals("reporter-edit-title")).findFirst().orElseThrow();
    }

    private TextArea editDescription() {
        return (TextArea) nodes(page).filter(TextArea.class::isInstance).map(TextArea.class::cast)
                .filter(field -> "reporter-edit-description".equals(field.getId())).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private ComboBox<IncidentCategory> editCategory() {
        return (ComboBox<IncidentCategory>) nodes(page).filter(ComboBox.class::isInstance)
                .map(ComboBox.class::cast)
                .filter(field -> field.getId().equals("reporter-edit-category")).findFirst().orElseThrow();
    }

    private TextArea followUp() {
        return (TextArea) nodes(page).filter(TextArea.class::isInstance).map(TextArea.class::cast)
                .filter(field -> "reporter-follow-up".equals(field.getId())).findFirst().orElseThrow();
    }

    private AttachmentPane attachmentPane() {
        return nodes(page).filter(AttachmentPane.class::isInstance)
                .map(AttachmentPane.class::cast).findFirst().orElseThrow();
    }

    private void assertNullForm() {
        assertNull(page.getBottom());
    }

    private static Stream<Node> nodes(Node node) {
        if (node instanceof ScrollPane scroll) {
            return Stream.concat(Stream.of(node), nodes(scroll.getContent()));
        }
        if (node instanceof Parent parent) {
            return Stream.concat(Stream.of(node),
                    parent.getChildrenUnmodifiable().stream().flatMap(ReporterIncidentPageTest::nodes));
        }
        return Stream.of(node);
    }

    private static List<String> labels(Node node) {
        return nodes(node).filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList();
    }

    private static Window waitForWindowWithLookup(String selector) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
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
                .filter(node -> node instanceof Button candidate && text.equals(candidate.getText()))
                .findFirst().orElseThrow()).fire();
    }

    private static void awaitFx(BooleanSupplier condition) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Timeline timer = onFx(() -> {
            Timeline poll = new Timeline(new KeyFrame(Duration.millis(20), event -> {
                if (condition.getAsBoolean()) {
                    ready.countDown();
                }
            }));
            poll.setCycleCount(Timeline.INDEFINITE);
            poll.play();
            return poll;
        });
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS), "UI condition was not reached");
        } finally {
            onFx(() -> {
                timer.stop();
                return null;
            });
        }
    }

    private static <T> T onFx(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}

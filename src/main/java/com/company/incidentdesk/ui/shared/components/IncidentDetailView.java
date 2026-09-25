package com.company.incidentdesk.ui.shared.components;

import java.util.Objects;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.comment.IncidentCommentService;
import com.company.incidentdesk.application.incident.IncidentDetailService;
import com.company.incidentdesk.application.presentation.IncidentDetailModel;
import com.company.incidentdesk.application.presentation.ResolutionModel;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.incident.IncidentId;

import javafx.concurrent.Task;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Shared role-independent incident detail renderer; all actions remain application-authorized. */
public final class IncidentDetailView extends VBox implements AutoCloseable {
    private static final Duration ACCESS_CHECK_INTERVAL = Duration.millis(250);
    private final IncidentDetailService details;
    private final IncidentCommentService comments;
    private final AttachmentPane attachments;
    private final IncidentId incidentId;
    private final Runnable onBack;
    private final IncidentDetailActions actions;
    private final AtomicLong revision = new AtomicLong();
    private Task<ApplicationResult<IncidentDetailModel>> activeLoad;
    private final ReadOnlyObjectWrapper<IncidentDetailState> state =
            new ReadOnlyObjectWrapper<>(this, "state", IncidentDetailState.loading());
    private boolean closed;
    private BooleanSupplier authorized = () -> false;
    private final Timeline accessChecks = new Timeline(new KeyFrame(ACCESS_CHECK_INTERVAL, event -> {
        if (state() instanceof IncidentDetailState.Ready && !authorized.getAsBoolean()) {
            showUnavailable();
        }
    }));

    public IncidentDetailView(IncidentDetailService details, IncidentCommentService comments,
            AttachmentService attachments, IncidentId incidentId, Runnable onBack,
            IncidentDetailActions actions) {
        super(14);
        this.details = Objects.requireNonNull(details, "details");
        this.comments = Objects.requireNonNull(comments, "comments");
        this.incidentId = Objects.requireNonNull(incidentId, "incidentId");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.attachments = new AttachmentPane(Objects.requireNonNull(attachments, "attachments"), incidentId);
        getStyleClass().add("incident-detail-view");
        setFillWidth(true);
        accessChecks.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                close();
            } else {
                if (closed) {
                    load();
                }
                accessChecks.playFromStart();
            }
        });
        render();
        load();
    }

    /** Rechecks authorization and reloads current incident details. */
    public void refresh() {
        load();
    }

    public IncidentDetailState state() {
        return state.get();
    }

    /** Observes loading and access changes without depending on the rendered node structure. */
    public ReadOnlyObjectProperty<IncidentDetailState> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /** Discards stale display data and pending reads after an access or session change. */
    public void showUnavailable() {
        cancelLoad();
        accessChecks.stop();
        attachments.close();
        authorized = () -> false;
        state.set(IncidentDetailState.unavailable());
        render();
    }

    private void load() {
        cancelLoad();
        closed = false;
        authorized = details.viewGuard(incidentId);
        state.set(IncidentDetailState.loading());
        render();
        if (getScene() != null) {
            accessChecks.playFromStart();
        }
        long request = revision.incrementAndGet();
        Task<ApplicationResult<IncidentDetailModel>> task = new Task<>() {
            @Override protected ApplicationResult<IncidentDetailModel> call() {
                return details.detail(incidentId);
            }
        };
        activeLoad = task;
        task.setOnSucceeded(event -> {
            if (request != revision.get() || closed) {
                return;
            }
            activeLoad = null;
            ApplicationResult<IncidentDetailModel> result = task.getValue();
            state.set(result.isSuccess() && authorized.getAsBoolean()
                    ? IncidentDetailState.ready(result.value().orElseThrow())
                    : IncidentDetailState.unavailable());
            render();
        });
        task.setOnFailed(event -> {
            if (request == revision.get() && !closed) {
                activeLoad = null;
                state.set(IncidentDetailState.unavailable());
                render();
            }
        });
        Thread.startVirtualThread(task);
    }

    private void render() {
        getChildren().clear();
        if (state() instanceof IncidentDetailState.Loading) {
            getChildren().add(backButton());
            getChildren().add(UiComponents.feedback("Loading incident", "Reading the latest authorized details.",
                    FeedbackType.LOADING));
        } else if (state() instanceof IncidentDetailState.Unavailable) {
            getChildren().add(backButton());
            getChildren().add(UiComponents.feedback("Incident unavailable",
                    "This incident cannot be displayed. Return to the dashboard and refresh.", FeedbackType.ERROR));
        } else if (state() instanceof IncidentDetailState.Ready ready) {
            Node content = readyContent(ready.model());
            VBox.setVgrow(content, Priority.ALWAYS);
            getChildren().add(content);
        }
    }

    private Node readyContent(IncidentDetailModel detail) {
        VBox page = new VBox(16);
        page.setPadding(new Insets(24));
        page.setFillWidth(true);
        page.getChildren().add(backButton());

        Label reference = new Label("Incident " + detail.summary().id().value());
        reference.getStyleClass().add("muted");
        Label title = new Label(detail.summary().title());
        title.getStyleClass().add("page-title");
        title.setWrapText(true);
        Label description = wrapped(detail.description());
        FlowPane badges = new FlowPane(8, 8);
        badges.getChildren().addAll(
                UiComponents.badge(detail.summary().categoryLabel(), SemanticTone.INFO),
                UiComponents.badge(detail.summary().statusLabel(), statusTone(detail.summary().statusLabel())));
        VBox header = new VBox(10, reference, title, badges, description);
        header.setMinWidth(0);
        page.getChildren().add(UiComponents.panel("Incident", header));

        FlowPane facts = new FlowPane(18, 12);
        facts.getChildren().addAll(
                fact("Reporter", detail.summary().reporterLabel()),
                fact("Assignee", detail.summary().assigneeLabel()),
                fact("Created", detail.summary().createdAt()),
                fact("Submitted", detail.submittedAt()),
                fact("Withdrawn", detail.withdrawnAt()),
                fact("Queue entered", detail.queue().enteredAt()),
                fact("First assigned", detail.queue().firstAssignedAt()),
                fact("Latest assignment", detail.queue().latestAssignedAt()));
        page.getChildren().add(UiComponents.panel("Details", facts));
        page.getChildren().add(new IncidentActionBar(incidentId, detail.summary().actions(), actions));

        page.getChildren().add(resolutionHistory(detail.resolutions()));
        page.getChildren().add(sloSection(detail));
        page.getChildren().add(commentSection(detail));
        if (detail.summary().actions().attachmentAccess()) {
            page.getChildren().add(attachments);
        } else {
            page.getChildren().add(UiComponents.panel("Attachments", UiComponents.feedback(
                    "Attachments unavailable", "No attachment access is available for this incident.",
                    FeedbackType.EMPTY)));
        }
        // Audit records have their own work item; keep a stable composition slot without implying audit data.
        page.getChildren().add(UiComponents.panel("Audit timeline", UiComponents.feedback(
                "Audit timeline not available yet", "Audit history is managed separately from incident details.",
                FeedbackType.EMPTY)));

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private Node resolutionHistory(List<ResolutionModel> resolutions) {
        VBox history = new VBox(10);
        if (resolutions.isEmpty()) {
            history.getChildren().add(UiComponents.feedback("No resolution history",
                    "Resolution remarks will appear here after an incident is resolved.", FeedbackType.EMPTY));
        } else {
            for (ResolutionModel resolution : resolutions) {
                VBox entry = new VBox(4,
                        new Label("Cycle " + resolution.cycleNumber() + " · " + resolution.resolvedAt()),
                        wrapped(resolution.remarks()),
                        new Label("Resolved by " + resolution.resolvedByLabel()));
                history.getChildren().add(UiComponents.panel("Resolution", entry));
            }
        }
        return UiComponents.panel("Resolution history", history);
    }

    private Node sloSection(IncidentDetailModel detail) {
        var slo = detail.slo();
        VBox content = new VBox(8,
                UiComponents.badge(slo.label(), slo.overdue() ? SemanticTone.DANGER : SemanticTone.NEUTRAL),
                UiComponents.sloProgress(Math.min(1, slo.progress()), "SLO progress: " + slo.label()));
        return UiComponents.panel("Service-level objective", content);
    }

    private Node commentSection(IncidentDetailModel detail) {
        IncidentCommentThread thread = new IncidentCommentThread();
        thread.setComments(detail.comments());
        thread.setComposerDisabled(!detail.summary().actions().comment());
        Label feedback = new Label();
        feedback.setWrapText(true);
        thread.setOnSubmitAsync(text -> submitComment(text, thread, feedback));
        return UiComponents.panel("Comments", thread, feedback);
    }

    private CompletionStage<Boolean> submitComment(String text, IncidentCommentThread thread, Label feedback) {
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        Thread.startVirtualThread(() -> {
            try {
                var result = comments.add(incidentId, text);
                if (!result.isSuccess()) {
                    Platform.runLater(() -> feedback.setText("Comment not added. Check access and try again."));
                    completion.complete(false);
                    return;
                }
                var refreshed = comments.list(incidentId);
                Platform.runLater(() -> {
                    if (refreshed.isSuccess()) {
                        thread.setComments(refreshed.value().orElseThrow());
                        feedback.setText("");
                    } else {
                        feedback.setText("Comment added, but the thread could not be refreshed.");
                    }
                });
                completion.complete(true);
            } catch (RuntimeException exception) {
                Platform.runLater(() -> feedback.setText("Comment not added. Check access and try again."));
                completion.completeExceptionally(exception);
            }
        });
        return completion;
    }

    private Node fact(String label, String value) {
        VBox fact = new VBox(3, new Label(label), wrapped(value.isBlank() ? "—" : value));
        fact.getStyleClass().add("incident-detail-fact");
        fact.setMinWidth(160);
        return fact;
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        return label;
    }

    private static SemanticTone statusTone(String status) {
        return switch (status) {
        case "Resolved" -> SemanticTone.SUCCESS;
        case "Withdrawn" -> SemanticTone.NEUTRAL;
        case "Assigned" -> SemanticTone.INFO;
        default -> SemanticTone.WARNING;
        };
    }

    private Button backButton() {
        Button back = UiComponents.action("Back to dashboard", ActionStyle.SECONDARY);
        back.setOnAction(event -> onBack.run());
        return back;
    }

    private void cancelLoad() {
        revision.incrementAndGet();
        if (activeLoad != null) {
            activeLoad.cancel();
            activeLoad = null;
        }
    }

    @Override
    public void close() {
        closed = true;
        showUnavailable();
    }
}

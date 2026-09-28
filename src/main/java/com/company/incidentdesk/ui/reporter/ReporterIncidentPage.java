package com.company.incidentdesk.ui.reporter;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.comment.IncidentCommentService;
import com.company.incidentdesk.application.incident.IncidentDetailService;
import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.incident.IncidentView;
import com.company.incidentdesk.application.presentation.IncidentDetailModel;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.ui.shared.components.ConfirmedIncidentAction;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.IncidentDetailActions;
import com.company.incidentdesk.ui.shared.components.IncidentDetailState;
import com.company.incidentdesk.ui.shared.components.IncidentDetailView;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.concurrent.Task;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Connects owning-Reporter lifecycle actions to the shared detail and attachment views. */
public final class ReporterIncidentPage extends BorderPane {
    private final IncidentId incidentId;
    private final IncidentDetailView detail;
    private final BooleanSupplier authorized;
    private final BooleanSupplier sessionMatches;
    private final Function<ReporterEditForm.Changes, ApplicationResult<IncidentView>> editOperation;
    private final Function<IncidentId, ApplicationResult<IncidentView>> withdrawOperation;
    private final BiFunction<IncidentId, String, ApplicationResult<IncidentView>> reopenOperation;
    private final VBox feedback = new VBox();
    private final ConfirmedIncidentAction withdrawAction;
    private Task<ApplicationResult<IncidentView>> activeOperation;
    private ReporterEditForm editForm;
    private ReporterFollowUpForm followUpForm;

    public ReporterIncidentPage(IncidentService incidents, IncidentDetailService details,
            IncidentCommentService comments, AttachmentService attachments, IncidentId incidentId, Runnable onBack) {
        this(editOperation(incidents, incidentId), Objects.requireNonNull(incidents, "incidents")::withdraw,
                Objects.requireNonNull(incidents, "incidents")::reopen,
                details, comments, attachments, incidentId, onBack);
    }

    ReporterIncidentPage(
            Function<ReporterEditForm.Changes, ApplicationResult<IncidentView>> editOperation,
            Function<IncidentId, ApplicationResult<IncidentView>> withdrawOperation,
            BiFunction<IncidentId, String, ApplicationResult<IncidentView>> reopenOperation,
            IncidentDetailService details, IncidentCommentService comments, AttachmentService attachments,
            IncidentId incidentId, Runnable onBack) {
        this.incidentId = Objects.requireNonNull(incidentId, "incidentId");
        this.editOperation = Objects.requireNonNull(editOperation, "editOperation");
        this.withdrawOperation = Objects.requireNonNull(withdrawOperation, "withdrawOperation");
        this.reopenOperation = Objects.requireNonNull(reopenOperation, "reopenOperation");
        Objects.requireNonNull(details, "details");
        authorized = details.viewGuard(incidentId);
        sessionMatches = details.sessionGuard();
        IncidentDetailActions actions = new IncidentDetailActions(Optional.of(this::openEdit),
                Optional.of(this::withdraw), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(this::openFollowUp));
        detail = new IncidentDetailView(details, Objects.requireNonNull(comments, "comments"),
                Objects.requireNonNull(attachments, "attachments"), incidentId, onBack, actions);
        detail.setAccessibleText("Incident detail for " + incidentId.value());
        withdrawAction = new ConfirmedIncidentAction(detail, feedback, authorized, sessionMatches);
        detail.stateProperty().addListener((observable, previous, current) -> {
            if (current instanceof IncidentDetailState.Unavailable) {
                discardForms();
            }
        });
        feedback.setId("reporter-incident-feedback");
        setTop(feedback);
        setCenter(detail);
        sceneProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                deactivate();
            }
        });
    }

    private static Function<ReporterEditForm.Changes, ApplicationResult<IncidentView>> editOperation(
            IncidentService incidents, IncidentId incidentId) {
        IncidentService requiredIncidents = Objects.requireNonNull(incidents, "incidents");
        IncidentId requiredIncidentId = Objects.requireNonNull(incidentId, "incidentId");
        return changes -> requiredIncidents.edit(requiredIncidentId, changes.title(), changes.description(),
                changes.category(), changes.anonymous());
    }

    private void openEdit(IncidentId id) {
        if (!canOpen(id, model -> model.summary().actions().edit())) {
            return;
        }
        IncidentDetailState.Ready ready = (IncidentDetailState.Ready) detail.state();
        var row = ready.model().summary();
        editForm = new ReporterEditForm(row.title(), ready.model().description(), category(row.categoryLabel()),
                row.anonymous(), this::saveEdit, this::cancelEdit);
        setBottom(editForm);
        feedback.getChildren().clear();
        editForm.focusTitle();
    }

    private void saveEdit(ReporterEditForm.Changes changes) {
        if (editForm == null || !canMutate(IncidentAction.EDIT)) {
            return;
        }
        editForm.setPending(true);
        runOperation("Saving changes", "Updating your incident.", () -> editOperation.apply(changes), result -> {
            if (result.isSuccess()) {
                discardEditForm();
                showSuccess("Incident updated", "Your changes have been saved.");
                detail.refresh();
                return;
            }
            showEditFailure(result.error().orElseThrow());
        });
    }

    private void showEditFailure(ApplicationError error) {
        if (editForm == null) {
            return;
        }
        editForm.setPending(false);
        if (error.code() == ApplicationErrorCode.VALIDATION) {
            editForm.showValidation(error.validation());
            feedback.getChildren().setAll(UiComponents.feedback(
                    "Check the incident details", "Correct the highlighted fields and try again.",
                    FeedbackType.ERROR));
        } else {
            showFailure(error.code() == ApplicationErrorCode.RESOURCE_UNAVAILABLE
                    ? "Incident is no longer editable" : "Incident not updated");
            if (error.code() == ApplicationErrorCode.RESOURCE_UNAVAILABLE) {
                editForm.setUnavailable();
                detail.refresh();
            }
        }
    }

    private void cancelEdit() {
        if (activeOperation == null) {
            discardEditForm();
            feedback.getChildren().clear();
        }
    }

    private void discardEditForm() {
        if (editForm != null) {
            editForm.clear();
            editForm = null;
            setBottom(null);
        }
    }

    private void withdraw(IncidentId id) {
        if (!canOpen(id, model -> model.summary().actions().withdraw())) {
            return;
        }
        withdrawAction.run("Withdraw incident", "This will cancel your incident",
                "Withdraw this incident? It will no longer be available to responders.", "Withdraw",
                "withdraw-confirmation", () -> withdrawOperation.apply(incidentId),
                "Withdrawing incident", "Saving your request.", result -> {
                    if (result.isSuccess()) {
                        showSuccess("Incident withdrawn", "Your incident has been withdrawn.");
                    } else {
                        showFailure("Incident not withdrawn");
                    }
                    detail.refresh();
                });
    }

    private void openFollowUp(IncidentId id) {
        if (!canOpen(id, model -> model.summary().actions().reopen())) {
            return;
        }
        followUpForm = new ReporterFollowUpForm(this::reopen, this::cancelFollowUp);
        setBottom(followUpForm);
        feedback.getChildren().clear();
        followUpForm.focusExplanation();
    }

    private void reopen(String explanation) {
        if (followUpForm == null || !canMutate(IncidentAction.REOPEN)) {
            return;
        }
        followUpForm.clearError();
        followUpForm.setPending(true);
        runOperation("Reopening incident", "Submitting your follow-up.",
                () -> reopenOperation.apply(incidentId, explanation), result -> {
                    if (result.isSuccess()) {
                        discardFollowUpForm();
                        showSuccess("Incident reopened", "Your follow-up has been saved and the incident requeued.");
                        detail.refresh();
                    } else {
                        showReopenFailure(result.error().orElseThrow());
                    }
                });
    }

    private void showReopenFailure(ApplicationError error) {
        if (followUpForm == null) {
            return;
        }
        followUpForm.setPending(false);
        if (error.code() == ApplicationErrorCode.VALIDATION) {
            followUpForm.showRequiredError();
            feedback.getChildren().setAll(UiComponents.feedback(
                    "Follow-up required", "Explain why the incident needs more work.", FeedbackType.ERROR));
        } else {
            showFailure(error.code() == ApplicationErrorCode.RESOURCE_UNAVAILABLE
                    ? "Incident is no longer resolved" : "Incident not reopened");
            if (error.code() == ApplicationErrorCode.RESOURCE_UNAVAILABLE) {
                followUpForm.setUnavailable();
                detail.refresh();
            }
        }
    }

    private void cancelFollowUp() {
        if (activeOperation == null) {
            discardFollowUpForm();
            feedback.getChildren().clear();
        }
    }

    private void discardFollowUpForm() {
        if (followUpForm != null) {
            followUpForm.clear();
            followUpForm = null;
            setBottom(null);
        }
    }

    private void runOperation(String title, String message, Supplier<ApplicationResult<IncidentView>> operation,
            Consumer<ApplicationResult<IncidentView>> onFinished) {
        detail.setDisable(true);
        feedback.getChildren().setAll(UiComponents.feedback(title, message, FeedbackType.LOADING));
        Task<ApplicationResult<IncidentView>> task = new Task<>() {
            @Override
            protected ApplicationResult<IncidentView> call() {
                return authorized.getAsBoolean() ? operation.get() : unavailable();
            }
        };
        activeOperation = task;
        task.setOnSucceeded(event -> finishOperation(task, task.getValue(), onFinished));
        task.setOnFailed(event -> finishOperation(task, unavailable(), onFinished));
        Thread.startVirtualThread(task);
    }

    private void finishOperation(Task<ApplicationResult<IncidentView>> task,
            ApplicationResult<IncidentView> result, Consumer<ApplicationResult<IncidentView>> onFinished) {
        if (activeOperation != task || getScene() == null) {
            return;
        }
        activeOperation = null;
        detail.setDisable(false);
        feedback.getChildren().clear();
        if (!sessionMatches.getAsBoolean()) {
            detail.showUnavailable();
            return;
        }
        onFinished.accept(result);
    }

    private boolean canOpen(IncidentId id, java.util.function.Predicate<IncidentDetailModel> actionAllowed) {
        if (!incidentId.equals(id) || busy() || getScene() == null) {
            return false;
        }
        if (!authorized.getAsBoolean()) {
            detail.showUnavailable();
            return false;
        }
        return detail.state() instanceof IncidentDetailState.Ready ready
                && actionAllowed.test(ready.model());
    }

    private boolean canMutate(IncidentAction action) {
        if (getScene() == null) {
            return false;
        }
        if (!authorized.getAsBoolean()) {
            detail.showUnavailable();
            return false;
        }
        if (!(detail.state() instanceof IncidentDetailState.Ready ready)) {
            return false;
        }
        return activeOperation == null && switch (action) {
        case EDIT -> ready.model().summary().actions().edit();
        case REOPEN -> ready.model().summary().actions().reopen();
        };
    }

    private void showSuccess(String title, String message) {
        feedback.getChildren().setAll(UiComponents.feedback(title, message, FeedbackType.SUCCESS));
    }

    private void showFailure(String title) {
        feedback.getChildren().setAll(UiComponents.feedback(title,
                "The incident's state or your access may have changed. Your entries are preserved.",
                FeedbackType.ERROR));
    }

    private boolean busy() {
        return activeOperation != null || withdrawAction.isActive() || editForm != null || followUpForm != null;
    }

    private void discardForms() {
        discardEditForm();
        discardFollowUpForm();
    }

    private void deactivate() {
        if (activeOperation != null) {
            activeOperation.cancel();
            activeOperation = null;
        }
        withdrawAction.cancel();
        discardForms();
        feedback.getChildren().clear();
        detail.setDisable(false);
        detail.close();
    }

    private static IncidentCategory category(String label) {
        for (IncidentCategory category : IncidentCategory.values()) {
            if (category.displayName().equals(label)) {
                return category;
            }
        }
        throw new IllegalStateException("Unknown incident category label: " + label);
    }

    private static ApplicationResult<IncidentView> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }

    private enum IncidentAction {
        EDIT,
        REOPEN
    }
}

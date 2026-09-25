package com.company.incidentdesk.ui.shared.components;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.company.incidentdesk.application.incident.IncidentView;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;

import javafx.concurrent.Task;
import javafx.scene.layout.VBox;

/**
 * Confirms, then runs, a single-shot consequential incident operation shared by role-specific detail
 * pages: shows a confirmation dialog, and only on acceptance disables the detail view, shows loading
 * feedback, runs the operation on a virtual thread, and reports the outcome once the completion is
 * still current.
 */
public final class ConfirmedIncidentAction {
    private final IncidentDetailView detail;
    private final VBox feedback;
    private final BooleanSupplier authorized;
    private final BooleanSupplier sessionMatches;
    private Task<ApplicationResult<IncidentView>> active;

    public ConfirmedIncidentAction(
            IncidentDetailView detail, VBox feedback, BooleanSupplier authorized, BooleanSupplier sessionMatches) {
        this.detail = Objects.requireNonNull(detail, "detail");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.authorized = Objects.requireNonNull(authorized, "authorized");
        this.sessionMatches = Objects.requireNonNull(sessionMatches, "sessionMatches");
    }

    /** True while a confirmed operation is running. */
    public boolean isActive() {
        return active != null;
    }

    /** Cancels an in-flight operation, for example when the page leaves the scene. */
    public void cancel() {
        if (active != null) {
            active.cancel();
            active = null;
        }
    }

    /**
     * Shows the confirmation dialog and, only when accepted, starts the operation.
     *
     * @param onFinished receives the outcome once this completion is still the current one
     */
    public void run(
            String confirmTitle, String confirmHeader, String confirmContent, String confirmText, String dialogId,
            Supplier<ApplicationResult<IncidentView>> operation, String loadingTitle, String loadingDetail,
            Consumer<ApplicationResult<IncidentView>> onFinished) {
        if (!UiComponents.confirm(confirmTitle, confirmHeader, confirmContent, confirmText, dialogId)
                || detail.getScene() == null) {
            return;
        }
        detail.setDisable(true);
        feedback.getChildren().setAll(UiComponents.feedback(loadingTitle, loadingDetail, FeedbackType.LOADING));
        Task<ApplicationResult<IncidentView>> task = new Task<>() {
            @Override protected ApplicationResult<IncidentView> call() {
                return authorized.getAsBoolean() ? operation.get() : unavailable();
            }
        };
        active = task;
        task.setOnSucceeded(event -> finish(task, task.getValue(), onFinished));
        task.setOnFailed(event -> finish(task, unavailable(), onFinished));
        Thread.startVirtualThread(task);
    }

    private void finish(
            Task<ApplicationResult<IncidentView>> task, ApplicationResult<IncidentView> result,
            Consumer<ApplicationResult<IncidentView>> onFinished) {
        if (active != task || detail.getScene() == null) {
            return;
        }
        active = null;
        detail.setDisable(false);
        feedback.getChildren().clear();
        if (!sessionMatches.getAsBoolean()) {
            detail.showUnavailable();
            return;
        }
        onFinished.accept(result);
    }

    private static ApplicationResult<IncidentView> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }
}

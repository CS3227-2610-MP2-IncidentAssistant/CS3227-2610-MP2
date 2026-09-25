package com.company.incidentdesk.ui.responder;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.BiFunction;

import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.comment.IncidentCommentService;
import com.company.incidentdesk.application.incident.IncidentDetailService;
import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.incident.IncidentView;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.IncidentDetailActions;
import com.company.incidentdesk.ui.shared.components.IncidentDetailState;
import com.company.incidentdesk.ui.shared.components.IncidentDetailView;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.concurrent.Task;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Connects responder actions to the shared detail view and application services. */
public final class ResponderIncidentPage extends BorderPane {
    private final IncidentId incidentId;
    private final IncidentDetailView detail;
    private final BooleanSupplier authorized;
    private final BooleanSupplier sessionMatches;
    private final Function<IncidentId, ApplicationResult<IncidentView>> claimOperation;
    private final BiFunction<IncidentId, String, ApplicationResult<IncidentView>> resolveOperation;
    private final Runnable onBack;
    private final VBox feedback = new VBox();
    private Task<ApplicationResult<IncidentView>> activeClaim;
    private Task<ApplicationResult<IncidentView>> activeResolution;
    private ResolutionForm resolutionForm;

    public ResponderIncidentPage(IncidentService incidents, IncidentDetailService details,
            IncidentCommentService comments, AttachmentService attachments, IncidentId incidentId, Runnable onBack) {
        this(incidents::claim, incidents::resolve, details, comments, attachments, incidentId, onBack);
    }

    ResponderIncidentPage(Function<IncidentId, ApplicationResult<IncidentView>> claimOperation,
            BiFunction<IncidentId, String, ApplicationResult<IncidentView>> resolveOperation,
            IncidentDetailService details, IncidentCommentService comments, AttachmentService attachments,
            IncidentId incidentId, Runnable onBack) {
        this.incidentId = Objects.requireNonNull(incidentId, "incidentId");
        this.claimOperation = Objects.requireNonNull(claimOperation, "claimOperation");
        this.resolveOperation = Objects.requireNonNull(resolveOperation, "resolveOperation");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
        authorized = details.viewGuard(incidentId);
        sessionMatches = details.sessionGuard();
        IncidentDetailActions actions = new IncidentDetailActions(Optional.empty(), Optional.empty(),
                Optional.of(this::claim), Optional.of(this::openResolution), Optional.empty(), Optional.empty(), Optional.empty());
        detail = new IncidentDetailView(details, comments, attachments, incidentId, onBack, actions);
        detail.stateProperty().addListener((observable, previous, current) -> {
            if (current instanceof IncidentDetailState.Unavailable) {
                discardResolutionForm();
            }
        });
        feedback.setId("responder-claim-feedback");
        setTop(feedback);
        setCenter(detail);
        sceneProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                deactivate();
            }
        });
    }

    void claim(IncidentId id) {
        if (activeClaim != null || activeResolution != null || resolutionForm != null
                || getScene() == null || !incidentId.equals(id)) {
            return;
        }
        if (!authorized.getAsBoolean()) {
            detail.showUnavailable();
            return;
        }
        if (!(detail.state() instanceof IncidentDetailState.Ready ready)
                || !ready.model().summary().actions().claim()) {
            return;
        }
        Task<ApplicationResult<IncidentView>> task = new Task<>() {
            @Override protected ApplicationResult<IncidentView> call() {
                return authorized.getAsBoolean() ? claimOperation.apply(incidentId) : unavailable();
            }
        };
        activeClaim = task;
        detail.setDisable(true);
        feedback.getChildren().setAll(UiComponents.feedback(
                "Claiming incident", "Saving your assignment.", FeedbackType.LOADING));
        task.setOnSucceeded(event -> finishClaim(task, task.getValue()));
        task.setOnFailed(event -> finishClaim(task, unavailable()));
        Thread.startVirtualThread(task);
    }

    private void finishClaim(Task<ApplicationResult<IncidentView>> task, ApplicationResult<IncidentView> result) {
        if (activeClaim != task || getScene() == null) {
            return;
        }
        activeClaim = null;
        detail.setDisable(false);
        if (!authorized.getAsBoolean()) {
            feedback.getChildren().clear();
            detail.showUnavailable();
            return;
        }
        if (result.isSuccess()) {
            feedback.getChildren().setAll(UiComponents.feedback("Incident claimed",
                    "This incident is now in My assigned incidents.", FeedbackType.SUCCESS));
        } else {
            feedback.getChildren().setAll(UiComponents.feedback("Claim not completed",
                    failureMessage(result.error().orElseThrow().code()), FeedbackType.ERROR));
        }
        // The retained dashboard reloads both queues when Back reattaches it to the scene.
        detail.refresh();
    }

    private static String failureMessage(ApplicationErrorCode code) {
        return switch (code) {
        case PERSISTENCE_FAILURE, CORRUPT_DATA -> "The claim could not be saved. Refresh and try again.";
        case ACCESS_DENIED, RESOURCE_UNAVAILABLE, INVALID_STATE, VALIDATION ->
                "This incident cannot be claimed. Its availability or your access may have changed.";
        };
    }

    private void openResolution(IncidentId id) {
        if (!incidentId.equals(id) || activeClaim != null || activeResolution != null || getScene() == null) {
            return;
        }
        if (!authorized.getAsBoolean()) {
            detail.showUnavailable();
            return;
        }
        if (!(detail.state() instanceof IncidentDetailState.Ready ready)
                || !ready.model().summary().actions().resolve()) {
            return;
        }
        if (resolutionForm == null) {
            resolutionForm = new ResolutionForm(this::resolve, this::cancelResolution);
            setBottom(resolutionForm);
        }
        feedback.getChildren().clear();
        resolutionForm.focusRemarks();
    }

    void resolve(String remarks) {
        if (resolutionForm == null || activeClaim != null || activeResolution != null || getScene() == null) {
            return;
        }
        if (!authorized.getAsBoolean()) {
            detail.showUnavailable();
            return;
        }
        resolutionForm.clearError();
        resolutionForm.setPending(true);
        detail.setDisable(true);
        feedback.getChildren().setAll(UiComponents.feedback(
                "Resolving incident", "Saving your resolution.", FeedbackType.LOADING));
        Task<ApplicationResult<IncidentView>> task = new Task<>() {
            @Override protected ApplicationResult<IncidentView> call() {
                return authorized.getAsBoolean() ? resolveOperation.apply(incidentId, remarks) : unavailable();
            }
        };
        activeResolution = task;
        task.setOnSucceeded(event -> finishResolution(task, task.getValue()));
        task.setOnFailed(event -> finishResolution(task, unavailable()));
        Thread.startVirtualThread(task);
    }

    private void finishResolution(Task<ApplicationResult<IncidentView>> task, ApplicationResult<IncidentView> result) {
        if (activeResolution != task || getScene() == null) {
            return;
        }
        activeResolution = null;
        detail.setDisable(false);
        feedback.getChildren().clear();
        if (!sessionMatches.getAsBoolean()) {
            detail.showUnavailable();
            return;
        }
        if (result.isSuccess()) {
            // Resolution removes responder access; do not reload or retain the former detail snapshot.
            detail.showUnavailable();
            onBack.run();
            return;
        }
        if (!authorized.getAsBoolean() || resolutionForm == null) {
            detail.showUnavailable();
            return;
        }
        resolutionForm.setPending(false);
        showResolutionFailure(result.error().orElseThrow().code());
    }

    private void showResolutionFailure(ApplicationErrorCode code) {
        switch (code) {
        case VALIDATION -> resolutionForm.showRequiredError();
        case PERSISTENCE_FAILURE, CORRUPT_DATA -> feedback.getChildren().setAll(UiComponents.feedback(
                "Resolution not saved", "Your remarks are preserved. Try again.", FeedbackType.ERROR));
        case ACCESS_DENIED, RESOURCE_UNAVAILABLE, INVALID_STATE -> {
            discardResolutionForm();
            detail.refresh();
            feedback.getChildren().setAll(UiComponents.feedback("Resolution not completed",
                    "The incident's availability or your access may have changed.", FeedbackType.ERROR));
        }
        }
    }

    private void cancelResolution() {
        if (activeResolution == null) {
            discardResolutionForm();
            feedback.getChildren().clear();
        }
    }

    private void discardResolutionForm() {
        if (resolutionForm != null) {
            resolutionForm.clear();
            resolutionForm = null;
            setBottom(null);
        }
    }

    private void deactivate() {
        if (activeResolution != null) {
            activeResolution.cancel();
            activeResolution = null;
        }
        discardResolutionForm();
        if (activeClaim != null) {
            activeClaim.cancel();
            activeClaim = null;
        }
        feedback.getChildren().clear();
        detail.setDisable(false);
        detail.close();
    }

    private static ApplicationResult<IncidentView> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }
}

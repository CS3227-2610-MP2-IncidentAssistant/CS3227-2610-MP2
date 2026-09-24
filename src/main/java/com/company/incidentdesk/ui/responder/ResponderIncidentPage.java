package com.company.incidentdesk.ui.responder;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

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
    private final Function<IncidentId, ApplicationResult<IncidentView>> claimOperation;
    private final VBox feedback = new VBox();
    private Task<ApplicationResult<IncidentView>> activeClaim;

    public ResponderIncidentPage(IncidentService incidents, IncidentDetailService details,
            IncidentCommentService comments, AttachmentService attachments, IncidentId incidentId, Runnable onBack) {
        this(incidents::claim, details, comments, attachments, incidentId, onBack);
    }

    ResponderIncidentPage(Function<IncidentId, ApplicationResult<IncidentView>> claimOperation,
            IncidentDetailService details, IncidentCommentService comments, AttachmentService attachments,
            IncidentId incidentId, Runnable onBack) {
        this.incidentId = Objects.requireNonNull(incidentId, "incidentId");
        this.claimOperation = Objects.requireNonNull(claimOperation, "claimOperation");
        authorized = details.viewGuard(incidentId);
        IncidentDetailActions actions = new IncidentDetailActions(Optional.empty(), Optional.empty(),
                Optional.of(this::claim), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        detail = new IncidentDetailView(details, comments, attachments, incidentId, onBack, actions);
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
        if (activeClaim != null || getScene() == null || !incidentId.equals(id)) {
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

    private void deactivate() {
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

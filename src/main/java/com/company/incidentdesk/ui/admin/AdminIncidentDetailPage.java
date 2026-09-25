package com.company.incidentdesk.ui.admin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import com.company.incidentdesk.application.account.AccountDirectoryService;
import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.comment.IncidentCommentService;
import com.company.incidentdesk.application.incident.IncidentDetailService;
import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.incident.IncidentView;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.ui.shared.components.ConfirmedIncidentAction;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.IncidentDetailActions;
import com.company.incidentdesk.ui.shared.components.IncidentDetailState;
import com.company.incidentdesk.ui.shared.components.IncidentDetailView;
import com.company.incidentdesk.ui.shared.components.IncidentFilterBar.AccountOption;
import com.company.incidentdesk.ui.shared.components.ResolutionForm;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.concurrent.Task;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Connects administrator resolve, reassign, and unassign actions to the shared detail view and application services. */
public final class AdminIncidentDetailPage extends BorderPane {
    private final IncidentId incidentId;
    private final IncidentDetailView detail;
    private final BooleanSupplier authorized;
    private final BooleanSupplier sessionMatches;
    private final Function<IncidentId, ApplicationResult<IncidentView>> detailOperation;
    private final BiFunction<IncidentId, String, ApplicationResult<IncidentView>> resolveOperation;
    private final BiFunction<IncidentId, AccountId, ApplicationResult<IncidentView>> reassignOperation;
    private final Function<IncidentId, ApplicationResult<IncidentView>> handoffOperation;
    private final Supplier<ApplicationResult<List<Account>>> eligibleResponderSource;
    private final VBox feedback = new VBox();
    private final ConfirmedIncidentAction handoffAction;
    private Task<ApplicationResult<IncidentView>> activeResolution;
    private Task<ApplicationResult<List<Account>>> activeEligibleLookup;
    private Task<ApplicationResult<IncidentView>> activeReassignment;
    private ResolutionForm resolutionForm;
    private ReassignmentForm reassignmentForm;

    public AdminIncidentDetailPage(IncidentService incidents, IncidentDetailService details,
            IncidentCommentService comments, AttachmentService attachments,
            AccountDirectoryService accountDirectory, IncidentId incidentId, Runnable onBack) {
        this(incidents::detail, incidents::resolve, incidents::reassign, incidents::handoff,
                accountDirectory::listAccounts, details, comments, attachments, incidentId, onBack);
    }

    AdminIncidentDetailPage(
            Function<IncidentId, ApplicationResult<IncidentView>> detailOperation,
            BiFunction<IncidentId, String, ApplicationResult<IncidentView>> resolveOperation,
            BiFunction<IncidentId, AccountId, ApplicationResult<IncidentView>> reassignOperation,
            Function<IncidentId, ApplicationResult<IncidentView>> handoffOperation,
            Supplier<ApplicationResult<List<Account>>> eligibleResponderSource,
            IncidentDetailService details, IncidentCommentService comments, AttachmentService attachments,
            IncidentId incidentId, Runnable onBack) {
        this.incidentId = Objects.requireNonNull(incidentId, "incidentId");
        this.detailOperation = Objects.requireNonNull(detailOperation, "detailOperation");
        this.resolveOperation = Objects.requireNonNull(resolveOperation, "resolveOperation");
        this.reassignOperation = Objects.requireNonNull(reassignOperation, "reassignOperation");
        this.handoffOperation = Objects.requireNonNull(handoffOperation, "handoffOperation");
        this.eligibleResponderSource = Objects.requireNonNull(eligibleResponderSource, "eligibleResponderSource");
        Objects.requireNonNull(onBack, "onBack");
        authorized = details.viewGuard(incidentId);
        sessionMatches = details.sessionGuard();
        IncidentDetailActions actions = new IncidentDetailActions(Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(this::openResolution), Optional.of(this::unassign),
                Optional.of(this::openReassignment), Optional.empty());
        detail = new IncidentDetailView(details, comments, attachments, incidentId, onBack, actions);
        handoffAction = new ConfirmedIncidentAction(detail, feedback, authorized, sessionMatches);
        detail.stateProperty().addListener((observable, previous, current) -> {
            if (current instanceof IncidentDetailState.Unavailable) {
                discardForms();
            }
        });
        feedback.setId("admin-incident-feedback");
        setTop(feedback);
        setCenter(detail);
        sceneProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                deactivate();
            }
        });
    }

    private void openResolution(IncidentId id) {
        if (!incidentId.equals(id) || busy() || getScene() == null) {
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
        if (resolutionForm == null || anyTaskActive() || getScene() == null) {
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
                "Resolving incident", "Saving the resolution.", FeedbackType.LOADING));
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
            discardResolutionForm();
            feedback.getChildren().setAll(UiComponents.feedback(
                    "Incident resolved", "The resolution has been saved.", FeedbackType.SUCCESS));
            detail.refresh();
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
                "Resolution not saved", "The remarks are preserved. Try again.", FeedbackType.ERROR));
        case ACCESS_DENIED, RESOURCE_UNAVAILABLE, INVALID_STATE -> {
            discardResolutionForm();
            detail.refresh();
            feedback.getChildren().setAll(UiComponents.feedback("Resolution not completed",
                    "The incident's availability may have changed.", FeedbackType.ERROR));
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

    private void openReassignment(IncidentId id) {
        if (!incidentId.equals(id) || busy() || getScene() == null) {
            return;
        }
        if (!authorized.getAsBoolean()) {
            detail.showUnavailable();
            return;
        }
        if (!(detail.state() instanceof IncidentDetailState.Ready ready)
                || !ready.model().summary().actions().reassign()) {
            return;
        }
        detail.setDisable(true);
        feedback.getChildren().setAll(UiComponents.feedback(
                "Loading eligible responders", "Preparing reassignment options.", FeedbackType.LOADING));
        Task<ApplicationResult<List<Account>>> task = new Task<>() {
            @Override protected ApplicationResult<List<Account>> call() {
                if (!authorized.getAsBoolean()) {
                    return unavailable();
                }
                ApplicationResult<IncidentView> detailResult = detailOperation.apply(incidentId);
                if (!detailResult.isSuccess()) {
                    return ApplicationResult.failure(detailResult.error().orElseThrow());
                }
                ApplicationResult<List<Account>> accountsResult = eligibleResponderSource.get();
                if (!accountsResult.isSuccess()) {
                    return accountsResult;
                }
                IncidentCategory category = detailResult.value().orElseThrow().category();
                List<Account> eligible = accountsResult.value().orElseThrow().stream()
                        .filter(account -> account.role() == Role.RESPONDER
                                && account.isEnabled()
                                && account.responderAccess().permits(category))
                        .toList();
                return ApplicationResult.success(eligible);
            }
        };
        activeEligibleLookup = task;
        task.setOnSucceeded(event -> finishEligibleLookup(task, task.getValue()));
        task.setOnFailed(event -> finishEligibleLookup(task, unavailable()));
        Thread.startVirtualThread(task);
    }

    private void finishEligibleLookup(
            Task<ApplicationResult<List<Account>>> task, ApplicationResult<List<Account>> result) {
        if (activeEligibleLookup != task || getScene() == null) {
            return;
        }
        activeEligibleLookup = null;
        detail.setDisable(false);
        feedback.getChildren().clear();
        if (!authorized.getAsBoolean()) {
            detail.showUnavailable();
            return;
        }
        if (!result.isSuccess()) {
            detail.refresh();
            feedback.getChildren().setAll(UiComponents.feedback("Reassignment not available",
                    "The incident's availability may have changed.", FeedbackType.ERROR));
            return;
        }
        if (!(detail.state() instanceof IncidentDetailState.Ready ready)
                || !ready.model().summary().actions().reassign()) {
            return;
        }
        List<Account> eligible = result.value().orElseThrow();
        if (eligible.isEmpty()) {
            feedback.getChildren().setAll(UiComponents.feedback("No eligible responders",
                    "No enabled responder currently has access to this incident's category.", FeedbackType.ERROR));
            return;
        }
        List<AccountOption> options = eligible.stream()
                .map(account -> new AccountOption(account.id(), account.loginName()))
                .toList();
        reassignmentForm = new ReassignmentForm(options, this::reassign, this::cancelReassignment);
        setBottom(reassignmentForm);
    }

    void reassign(AccountId responderId) {
        if (reassignmentForm == null || anyTaskActive() || getScene() == null) {
            return;
        }
        if (!authorized.getAsBoolean()) {
            detail.showUnavailable();
            return;
        }
        reassignmentForm.clearError();
        reassignmentForm.setPending(true);
        detail.setDisable(true);
        feedback.getChildren().setAll(UiComponents.feedback(
                "Reassigning incident", "Saving the new assignment.", FeedbackType.LOADING));
        Task<ApplicationResult<IncidentView>> task = new Task<>() {
            @Override protected ApplicationResult<IncidentView> call() {
                return authorized.getAsBoolean() ? reassignOperation.apply(incidentId, responderId) : unavailable();
            }
        };
        activeReassignment = task;
        task.setOnSucceeded(event -> finishReassignment(task, task.getValue()));
        task.setOnFailed(event -> finishReassignment(task, unavailable()));
        Thread.startVirtualThread(task);
    }

    private void finishReassignment(Task<ApplicationResult<IncidentView>> task, ApplicationResult<IncidentView> result) {
        if (activeReassignment != task || getScene() == null) {
            return;
        }
        activeReassignment = null;
        detail.setDisable(false);
        feedback.getChildren().clear();
        if (!sessionMatches.getAsBoolean()) {
            detail.showUnavailable();
            return;
        }
        discardReassignmentForm();
        if (result.isSuccess()) {
            feedback.getChildren().setAll(UiComponents.feedback(
                    "Incident reassigned", "The new responder is now assigned.", FeedbackType.SUCCESS));
        } else {
            feedback.getChildren().setAll(UiComponents.feedback("Reassignment not completed",
                    "The incident's availability or the responder's eligibility may have changed.",
                    FeedbackType.ERROR));
        }
        detail.refresh();
    }

    private void cancelReassignment() {
        if (activeReassignment == null) {
            discardReassignmentForm();
            feedback.getChildren().clear();
        }
    }

    private void discardReassignmentForm() {
        if (reassignmentForm != null) {
            reassignmentForm.clear();
            reassignmentForm = null;
            setBottom(null);
        }
    }

    void unassign(IncidentId id) {
        if (!incidentId.equals(id) || busy() || getScene() == null) {
            return;
        }
        if (!authorized.getAsBoolean()) {
            detail.showUnavailable();
            return;
        }
        if (!(detail.state() instanceof IncidentDetailState.Ready ready)
                || !ready.model().summary().actions().handoff()) {
            return;
        }
        handoffAction.run(
                "Unassign incident", "The current responder will lose access to this incident",
                "Unassign this incident and return it to its category queue?", "Unassign",
                "unassign-confirmation", () -> handoffOperation.apply(incidentId),
                "Unassigning incident", "Returning the incident to its queue.", this::finishUnassign);
    }

    private void finishUnassign(ApplicationResult<IncidentView> result) {
        if (result.isSuccess()) {
            feedback.getChildren().setAll(UiComponents.feedback(
                    "Incident unassigned", "The incident has been returned to its category queue.",
                    FeedbackType.SUCCESS));
        } else {
            feedback.getChildren().setAll(UiComponents.feedback("Unassignment not completed",
                    "The incident's availability may have changed.", FeedbackType.ERROR));
        }
        detail.refresh();
    }

    private void discardForms() {
        discardResolutionForm();
        discardReassignmentForm();
    }

    private boolean anyTaskActive() {
        return activeResolution != null || activeReassignment != null || activeEligibleLookup != null;
    }

    private boolean busy() {
        return anyTaskActive() || handoffAction.isActive() || resolutionForm != null || reassignmentForm != null;
    }

    private void deactivate() {
        if (activeResolution != null) {
            activeResolution.cancel();
            activeResolution = null;
        }
        if (activeEligibleLookup != null) {
            activeEligibleLookup.cancel();
            activeEligibleLookup = null;
        }
        if (activeReassignment != null) {
            activeReassignment.cancel();
            activeReassignment = null;
        }
        handoffAction.cancel();
        discardForms();
        feedback.getChildren().clear();
        detail.setDisable(false);
        detail.close();
    }

    private static <T> ApplicationResult<T> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }
}

package com.company.incidentdesk.ui.reporter;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.incident.IncidentView;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.persistence.IncidentSearchCriteria;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.IncidentTable;
import com.company.incidentdesk.ui.shared.components.IncidentTableConfiguration;
import com.company.incidentdesk.ui.shared.components.IncidentTableState;
import com.company.incidentdesk.ui.shared.components.RolePageLayout;
import com.company.incidentdesk.ui.shared.components.UiComponents;

/** Reporter workspace for submitting and tracking the current account's incidents. */
public final class ReporterPage extends RolePageLayout {
    private final IncidentSubmissionForm form;
    private final Function<IncidentSearchCriteria, ApplicationResult<List<IncidentRowModel>>> searchIncidents;
    private final Consumer<IncidentId> onOpenDetail;
    private final IncidentTable incidents = new IncidentTable(IncidentTableConfiguration.reporter());
    private final Button refresh = UiComponents.action("Refresh", ActionStyle.SECONDARY);
    private final Button open = UiComponents.action("Open details", ActionStyle.PRIMARY);
    private Task<ApplicationResult<List<IncidentRowModel>>> activeLoad;
    private long loadVersion;

    /** Creates the dashboard hosted by the authenticated shell. */
    public ReporterPage() {
        this(submission -> ApplicationResult.failure(
                ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE)),
                ReporterPage::unavailableRows, ignored -> { });
    }

    /** Retains the submission-only constructor for existing integrations. */
    public ReporterPage(IncidentService incidents) {
        this(submissionOperation(incidents), ReporterPage::unavailableRows, ignored -> { });
    }

    /** Creates the authorized reporter dashboard used by the shared navigation shell. */
    public ReporterPage(IncidentService incidents, IncidentPresentationMapper mapper,
            Consumer<IncidentId> onOpenDetail) {
        this(submissionOperation(incidents),
                criteria -> incidents.reporterIncidents(criteria, mapper), onOpenDetail);
    }

    ReporterPage(Function<IncidentSubmissionForm.Submission, ApplicationResult<IncidentView>> submitIncident) {
        this(submitIncident, ReporterPage::unavailableRows, ignored -> { });
    }

    ReporterPage(Function<IncidentSubmissionForm.Submission, ApplicationResult<IncidentView>> submitIncident,
            Function<IncidentSearchCriteria, ApplicationResult<List<IncidentRowModel>>> searchIncidents,
            Consumer<IncidentId> onOpenDetail) {
        super("Reporter", "Create incident reports and track the incidents you submitted.");
        Objects.requireNonNull(submitIncident, "submitIncident");
        this.searchIncidents = Objects.requireNonNull(searchIncidents, "searchIncidents");
        this.onOpenDetail = Objects.requireNonNull(onOpenDetail, "onOpenDetail");
        VBox feedback = new VBox();
        form = new IncidentSubmissionForm(submission -> formSubmission(submission, submitIncident, feedback));

        configureContent(feedback);
        configureIncidentActions();
        configurePageLifecycle();
    }

    private void configureContent(VBox feedback) {
        VBox content = (VBox) getCenter();
        content.setAlignment(Pos.TOP_LEFT);
        content.getChildren().add(2, UiComponents.panel("New incident", form));
        content.getChildren().add(3, feedback);
        content.getChildren().add(4, UiComponents.panel("My incidents",
                new VBox(12, new HBox(12, refresh, open), incidents)));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        setCenter(scroll);
    }

    private void configureIncidentActions() {
        refresh.setOnAction(event -> refreshIncidents());
        open.setDisable(true);
        open.setOnAction(event -> openSelected());
        incidents.setTableAccessibleText("My incidents");
        incidents.setPrefHeight(320);
        incidents.setOnRefresh(criteria -> refreshIncidents());
        incidents.setOnOpenDetail(row -> this.onOpenDetail.accept(row.id()));
        incidents.selectedRowProperty().addListener((observable, previous, selected) ->
                open.setDisable(selected == null));
    }

    private void configurePageLifecycle() {
        sceneProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                cancelLoad();
                incidents.clearSelection();
                incidents.setState(IncidentTableState.loading());
            } else {
                refreshIncidents();
            }
        });
    }

    private void openSelected() {
        IncidentRowModel selected = incidents.selectedRowProperty().get();
        if (selected != null) {
            onOpenDetail.accept(selected.id());
        }
    }

    /** Reloads only the incidents authorized for the active reporter session. */
    public void refreshIncidents() {
        cancelLoad();
        long request = ++loadVersion;
        incidents.setState(IncidentTableState.loading());
        refresh.setDisable(true);
        IncidentSearchCriteria criteria = incidents.criteria();
        Task<ApplicationResult<List<IncidentRowModel>>> task = new Task<>() {
            @Override protected ApplicationResult<List<IncidentRowModel>> call() {
                return searchIncidents.apply(criteria);
            }
        };
        activeLoad = task;
        task.setOnSucceeded(event -> finishLoad(request, task.getValue()));
        task.setOnFailed(event -> finishLoad(request, ApplicationResult.failure(
                ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE))));
        Thread.startVirtualThread(task);
    }

    private void finishLoad(long request, ApplicationResult<List<IncidentRowModel>> result) {
        if (request != loadVersion || getScene() == null) {
            return;
        }
        activeLoad = null;
        refresh.setDisable(false);
        if (result.isSuccess()) {
            incidents.setState(IncidentTableState.loaded(result.value().orElseThrow()));
        } else {
            incidents.setState(IncidentTableState.error("Try again. If the problem persists, sign in again."));
        }
    }

    private void cancelLoad() {
        loadVersion++;
        if (activeLoad != null) {
            activeLoad.cancel();
            activeLoad = null;
        }
    }

    private static Function<IncidentSubmissionForm.Submission, ApplicationResult<IncidentView>> submissionOperation(
            IncidentService incidents) {
        IncidentService requiredIncidents = Objects.requireNonNull(incidents, "incidents");
        return submission -> requiredIncidents.submit(
                submission.title(), submission.description(), submission.category(), false);
    }

    private static ApplicationResult<List<IncidentRowModel>> unavailableRows(IncidentSearchCriteria criteria) {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }

    private void formSubmission(IncidentSubmissionForm.Submission submission,
            Function<IncidentSubmissionForm.Submission, ApplicationResult<IncidentView>> submitIncident,
            VBox feedback) {
        form.setDisable(true);
        feedback.getChildren().setAll(UiComponents.feedback(
                "Submitting incident", "Saving your report.", FeedbackType.LOADING));
        Task<ApplicationResult<IncidentView>> task = new Task<>() {
            @Override protected ApplicationResult<IncidentView> call() {
                return submitIncident.apply(submission);
            }
        };
        task.setOnSucceeded(event -> {
            form.setDisable(false);
            ApplicationResult<IncidentView> result = task.getValue();
            if (result.isSuccess()) {
                form.clearAfterSuccess();
                feedback.getChildren().setAll(UiComponents.feedback(
                        "Incident submitted", "Your report has been saved.", FeedbackType.SUCCESS));
                if (getScene() != null) {
                    refreshIncidents();
                }
            } else {
                showFailure(form, feedback, result.error().orElseThrow());
            }
        });
        task.setOnFailed(event -> {
            form.setDisable(false);
            feedback.getChildren().setAll(UiComponents.feedback(
                    "Submission failed", "Your report was not saved. Please try again.", FeedbackType.ERROR));
        });
        Thread.startVirtualThread(task);
    }

    private static void showFailure(IncidentSubmissionForm form, VBox feedback, ApplicationError error) {
        if (error.code() == ApplicationErrorCode.VALIDATION) {
            form.showServiceValidation(error.validation());
            feedback.getChildren().setAll(UiComponents.feedback(
                    "Check your report", "Correct the highlighted fields and try again.", FeedbackType.ERROR));
            return;
        }
        String heading = error.code() == ApplicationErrorCode.RESOURCE_UNAVAILABLE
                ? "Submission unavailable" : "Submission failed";
        String message = error.code() == ApplicationErrorCode.RESOURCE_UNAVAILABLE
                ? "Your session is unavailable. Sign in and try again."
                : "Your report was not saved. Please try again.";
        feedback.getChildren().setAll(UiComponents.feedback(heading, message, FeedbackType.ERROR));
    }
}

package com.company.incidentdesk.ui.reporter;

import java.util.Objects;
import java.util.function.Function;

import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;

import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.incident.IncidentView;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.RolePageLayout;
import com.company.incidentdesk.ui.shared.components.UiComponents;

/** Initial workspace for incident reporters. */
public final class ReporterPage extends RolePageLayout {
    private final IncidentSubmissionForm form;

    /** Creates the dashboard hosted by the authenticated shell. */
    public ReporterPage() {
        this(submission -> ApplicationResult.failure(
                ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE)));
    }

    /** Creates the authenticated reporter dashboard. */
    public ReporterPage(IncidentService incidents) {
        this(submissionOperation(incidents));
    }

    ReporterPage(Function<IncidentSubmissionForm.Submission, ApplicationResult<IncidentView>> submitIncident) {
        super("Reporter", "Create incident reports and track the incidents you submitted.");
        Objects.requireNonNull(submitIncident, "submitIncident");
        VBox feedback = new VBox();
        form = new IncidentSubmissionForm(submission -> formSubmission(submission, submitIncident, feedback));

        VBox content = (VBox) getCenter();
        content.setAlignment(Pos.TOP_LEFT);
        content.getChildren().add(2, UiComponents.panel("New incident", form));
        content.getChildren().add(3, feedback);
    }

    private static Function<IncidentSubmissionForm.Submission, ApplicationResult<IncidentView>> submissionOperation(
            IncidentService incidents) {
        IncidentService requiredIncidents = Objects.requireNonNull(incidents, "incidents");
        return submission -> requiredIncidents.submit(
                submission.title(), submission.description(), submission.category(), false);
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

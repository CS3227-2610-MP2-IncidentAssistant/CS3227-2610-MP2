package com.company.incidentdesk.ui.reporter;

import javafx.geometry.Pos;
import javafx.scene.layout.VBox;

import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.RolePageLayout;
import com.company.incidentdesk.ui.shared.components.UiComponents;

/** Initial workspace for incident reporters. */
public final class ReporterPage extends RolePageLayout {
    /**
     * Creates the reporter page.
     *
     * @param onBack action that returns to role selection
     */
    public ReporterPage(Runnable onBack) {
        super(
                "Reporter",
                "Create incident reports and track the incidents you submitted.",
                onBack);

        VBox feedback = new VBox();
        IncidentSubmissionForm form = new IncidentSubmissionForm(submission ->
                feedback.getChildren().setAll(UiComponents.feedback(
                        "Submission unavailable",
                        "The shared incident service is not connected yet. Your report has not been saved.",
                        FeedbackType.ERROR)));

        VBox content = (VBox) getCenter();
        content.setAlignment(Pos.TOP_LEFT);
        content.getChildren().add(2, UiComponents.panel("New incident", form));
        content.getChildren().add(3, feedback);
    }
}

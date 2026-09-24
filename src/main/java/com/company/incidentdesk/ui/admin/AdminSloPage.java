package com.company.incidentdesk.ui.admin;

import java.time.Duration;

import com.company.incidentdesk.application.slo.SloConfigurationService;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Administrator SLO configuration overview. */
public final class AdminSloPage extends BorderPane {
    public AdminSloPage(SloConfigurationService service) {
        Label title = new Label("SLO configuration");
        title.getStyleClass().add("page-title");
        VBox content = new VBox(16, title);
        content.setPadding(new Insets(24));
        var result = service.currentTargets();
        if (!result.isSuccess()) {
            content.getChildren().add(UiComponents.feedback(
                    "SLO configuration unavailable", "Sign in as an administrator and try again.",
                    FeedbackType.ERROR));
        } else if (result.value().orElseThrow().isEmpty()) {
            content.getChildren().add(UiComponents.feedback(
                    "No SLO targets configured", "Category targets will appear here once configured.",
                    FeedbackType.EMPTY));
        } else {
            FlowPane targets = new FlowPane(12, 12);
            result.value().orElseThrow().stream().map(this::targetCard)
                    .forEach(targets.getChildren()::add);
            content.getChildren().add(UiComponents.panel("Current targets", targets));
        }
        setCenter(content);
    }

    private VBox targetCard(SloTargetVersion version) {
        String detail = "Claim " + duration(version.target().timeToClaimTarget())
                + " · In progress " + duration(version.target().timeInProgressTarget())
                + " · Reopen " + Math.round(version.target().reopenRateTarget() * 100) + "%";
        return UiComponents.metricCard(version.category().name(), "Configured", detail);
    }

    private String duration(Duration duration) {
        return duration.toHours() + "h " + duration.toMinutesPart() + "m";
    }
}

package com.company.incidentdesk.ui.admin;

import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;

/** Shared three-card presentation of one category's effective SLO target. */
final class SloTargetMetrics {
    private static final int METRIC_COUNT = 3;

    private SloTargetMetrics() { }

    static GridPane create(SloTargetVersion version) {
        GridPane metrics = new GridPane();
        metrics.setHgap(14);
        metrics.setVgap(14);
        for (int column = 0; column < METRIC_COUNT; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / METRIC_COUNT);
            metrics.getColumnConstraints().add(constraints);
        }
        metrics.add(UiComponents.metricCard(
                "Average time to claim",
                version == null ? "—" : SloTargetFormat.duration(version.target().timeToClaimTarget()),
                version == null ? "Not configured" : "Elapsed wall-clock target"), 0, 0);
        metrics.add(UiComponents.metricCard(
                "Average time in progress",
                version == null ? "—" : SloTargetFormat.duration(version.target().timeInProgressTarget()),
                version == null ? "Not configured" : "Elapsed wall-clock target"), 1, 0);
        metrics.add(UiComponents.metricCard(
                "Average reopen rate",
                version == null ? "—" : SloTargetFormat.percent(version.target().reopenRateTarget()),
                version == null ? "Not configured" : "Target across resolved incidents"), 2, 0);
        return metrics;
    }
}

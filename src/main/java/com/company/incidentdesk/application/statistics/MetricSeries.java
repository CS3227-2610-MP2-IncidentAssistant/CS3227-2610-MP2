package com.company.incidentdesk.application.statistics;

import java.util.List;
import java.util.Objects;

/**
 * One chart/table-neutral metric, reusable by different dashboards.
 *
 * @param metricKey stable identifier for the metric (e.g. {@code "resolved_incidents"})
 * @param title human-readable metric title
 * @param valueUnit unit of {@link MetricSeriesPoint#value()}
 * @param points one point per grouping (e.g. one per responder), empty for no data
 */
public record MetricSeries(String metricKey, String title, MetricUnit valueUnit, List<MetricSeriesPoint> points) {
    public MetricSeries {
        Objects.requireNonNull(metricKey, "metricKey");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(valueUnit, "valueUnit");
        points = List.copyOf(Objects.requireNonNull(points, "points"));
    }
}

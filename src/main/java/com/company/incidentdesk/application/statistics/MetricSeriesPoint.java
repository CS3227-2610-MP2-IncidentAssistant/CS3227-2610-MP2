package com.company.incidentdesk.application.statistics;

import java.util.Objects;

/**
 * One chart/table-neutral data point: a privacy-safe label, a numeric value, and its sample size.
 *
 * @param label display-safe category label (e.g. a responder's login name), never a raw identifier
 * @param value metric value in the unit declared by the owning {@link MetricSeries#valueUnit()}
 * @param sampleSize number of underlying resolution events the value was computed from
 */
public record MetricSeriesPoint(String label, double value, long sampleSize) {
    public MetricSeriesPoint {
        Objects.requireNonNull(label, "label");
        if (sampleSize < 0) {
            throw new IllegalArgumentException("sampleSize must not be negative");
        }
    }
}

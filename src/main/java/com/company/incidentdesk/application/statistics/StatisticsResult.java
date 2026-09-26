package com.company.incidentdesk.application.statistics;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.statistics.StatisticsSummary;

/**
 * Chart/table-neutral statistics result, shared by responder-own and administrator-wide views.
 *
 * @param companySummary incident-perspective aggregate (including time-to-claim); present only for
 *         the administrator-wide view, since time-to-claim is not meaningfully responder-scoped
 * @param responders per-responder breakdown: exactly one entry for a responder's own performance
 *         view, or one entry per matching responder for an administrator-wide grouped view
 * @param series chart/table-neutral series derived from {@code responders}, empty when ungrouped
 */
public record StatisticsResult(
        Optional<StatisticsSummary> companySummary,
        List<ResponderStatisticsView> responders,
        List<MetricSeries> series) {
    public StatisticsResult {
        Objects.requireNonNull(companySummary, "companySummary");
        responders = List.copyOf(Objects.requireNonNull(responders, "responders"));
        series = List.copyOf(Objects.requireNonNull(series, "series"));
    }
}

package com.company.incidentdesk.domain.statistics;

import java.util.Objects;

import com.company.incidentdesk.domain.slo.SloEvaluation;

/**
 * Incident-perspective operational statistics for a selected category/period scope.
 *
 * <p>Wraps the same claim/progress/reopen aggregate used for SLO evaluation and adds the count of
 * resolutions completed by an administrator rather than the assigned responder. Every resolution
 * requires an assigned responder, so an administrator-resolved case is always also attributed to
 * that responder; {@code administratorResolvedCount} lets callers report it separately as required
 * by the attribution rules in {@code .agents/slo.md}.
 */
public record StatisticsSummary(SloEvaluation aggregate, int administratorResolvedCount) {
    public StatisticsSummary {
        Objects.requireNonNull(aggregate, "aggregate");
        if (administratorResolvedCount < 0) {
            throw new IllegalArgumentException("administratorResolvedCount must not be negative");
        }
        if (administratorResolvedCount > aggregate.completedCycleCount()) {
            throw new IllegalArgumentException("administratorResolvedCount cannot exceed completedCycleCount");
        }
    }

    /** Returns the deterministic empty summary for an empty scope. */
    public static StatisticsSummary empty() {
        return new StatisticsSummary(SloEvaluation.empty(), 0);
    }
}

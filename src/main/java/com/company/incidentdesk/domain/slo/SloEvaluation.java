package com.company.incidentdesk.domain.slo;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Aggregated, deterministic SLO metrics for a selected incident scope and time period. */
public record SloEvaluation(
        Optional<Duration> averageTimeToClaim,
        int claimedCycleCount,
        Optional<Duration> averageTimeInProgress,
        int completedCycleCount,
        Optional<Double> reopenRate,
        int reopenedIncidentCount,
        int resolvedIncidentCount,
        int reopenEventCount) {
    /**
     * Creates and validates an aggregated SLO evaluation.
     *
     * @param averageTimeToClaim average elapsed time to claim over claimed cycles
     * @param claimedCycleCount number of cycles that were claimed at least once
     * @param averageTimeInProgress average elapsed time in progress over completed cycles
     * @param completedCycleCount number of cycles that reached resolution
     * @param reopenRate reopened-incident count divided by resolved-incident count
     * @param reopenedIncidentCount number of resolved incidents reopened at least once
     * @param resolvedIncidentCount number of incidents resolved at least once
     * @param reopenEventCount total reopen events, counting an incident once per reopen
     */
    public SloEvaluation {
        Objects.requireNonNull(averageTimeToClaim, "averageTimeToClaim");
        Objects.requireNonNull(averageTimeInProgress, "averageTimeInProgress");
        Objects.requireNonNull(reopenRate, "reopenRate");
        requireNonNegative(claimedCycleCount, "claimedCycleCount");
        requireNonNegative(completedCycleCount, "completedCycleCount");
        requireNonNegative(reopenedIncidentCount, "reopenedIncidentCount");
        requireNonNegative(resolvedIncidentCount, "resolvedIncidentCount");
        requireNonNegative(reopenEventCount, "reopenEventCount");
        if (averageTimeToClaim.isPresent() != (claimedCycleCount > 0)) {
            throw new IllegalArgumentException("averageTimeToClaim must be present only with claimed cycles");
        }
        if (averageTimeInProgress.isPresent() != (completedCycleCount > 0)) {
            throw new IllegalArgumentException("averageTimeInProgress must be present only with completed cycles");
        }
        if (reopenRate.isPresent() != (resolvedIncidentCount > 0)) {
            throw new IllegalArgumentException("reopenRate must be present only with resolved incidents");
        }
        if (reopenedIncidentCount > resolvedIncidentCount) {
            throw new IllegalArgumentException("reopenedIncidentCount cannot exceed resolvedIncidentCount");
        }
        reopenRate.ifPresent(rate -> {
            if (!Double.isFinite(rate) || rate < 0 || rate > 1) {
                throw new IllegalArgumentException("reopenRate must be between 0 and 1");
            }
        });
    }

    /** Returns the deterministic empty evaluation for an empty incident scope. */
    public static SloEvaluation empty() {
        return new SloEvaluation(Optional.empty(), 0, Optional.empty(), 0, Optional.empty(), 0, 0, 0);
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}

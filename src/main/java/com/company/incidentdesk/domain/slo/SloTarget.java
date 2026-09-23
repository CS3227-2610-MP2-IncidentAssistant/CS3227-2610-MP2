package com.company.incidentdesk.domain.slo;

import java.time.Duration;
import java.util.Objects;

/** Independently configurable per-category SLO thresholds. */
public record SloTarget(Duration timeToClaimTarget, Duration timeInProgressTarget, double reopenRateTarget) {
    /**
     * Creates and validates a set of category SLO thresholds.
     *
     * @param timeToClaimTarget maximum acceptable elapsed time to claim
     * @param timeInProgressTarget maximum acceptable elapsed time in progress
     * @param reopenRateTarget maximum acceptable reopen rate, from 0.0 to 1.0
     */
    public SloTarget {
        Objects.requireNonNull(timeToClaimTarget, "timeToClaimTarget");
        Objects.requireNonNull(timeInProgressTarget, "timeInProgressTarget");
        if (timeToClaimTarget.isNegative()) {
            throw new IllegalArgumentException("timeToClaimTarget must not be negative");
        }
        if (timeInProgressTarget.isNegative()) {
            throw new IllegalArgumentException("timeInProgressTarget must not be negative");
        }
        if (!Double.isFinite(reopenRateTarget) || reopenRateTarget < 0 || reopenRateTarget > 1) {
            throw new IllegalArgumentException("reopenRateTarget must be between 0 and 1");
        }
    }
}

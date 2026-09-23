package com.company.incidentdesk.domain.slo;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Reusable live SLO badge state for one incident's current lifecycle position. */
public record SloStatusModel(
        SloComplianceState state,
        Optional<SloLiveMetricType> metric,
        Optional<Duration> elapsed,
        Optional<Duration> target) {
    /**
     * Creates and validates a live SLO status.
     *
     * @param state overall compliance state
     * @param metric metric measured, present only when state is not {@code NOT_APPLICABLE}
     * @param elapsed elapsed duration for the current cycle, present only when evaluated
     * @param target applicable configured target, present only when evaluated
     */
    public SloStatusModel {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(target, "target");
        if (state == SloComplianceState.NOT_APPLICABLE) {
            if (metric.isPresent() || elapsed.isPresent() || target.isPresent()) {
                throw new IllegalArgumentException("not-applicable status must not carry metric details");
            }
        } else if (metric.isEmpty() || elapsed.isEmpty() || target.isEmpty()) {
            throw new IllegalArgumentException("evaluated status requires metric, elapsed, and target");
        }
    }

    /** Returns the shared status for an incident with no applicable live metric. */
    public static SloStatusModel notApplicable() {
        return new SloStatusModel(SloComplianceState.NOT_APPLICABLE, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Creates an evaluated status, overdue when the elapsed duration exceeds the target. */
    public static SloStatusModel evaluated(SloLiveMetricType metric, Duration elapsed, Duration target) {
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(target, "target");
        SloComplianceState state = elapsed.compareTo(target) > 0
                ? SloComplianceState.OVERDUE
                : SloComplianceState.WITHIN_TARGET;
        return new SloStatusModel(state, Optional.of(metric), Optional.of(elapsed), Optional.of(target));
    }
}

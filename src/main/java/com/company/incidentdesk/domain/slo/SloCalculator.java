package com.company.incidentdesk.domain.slo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.domain.incident.ResolutionCycle;

/** Pure, deterministic SLO metric and live-status calculations over persisted lifecycle timestamps. */
public final class SloCalculator {
    private SloCalculator() {
    }

    /**
     * Returns the elapsed time to claim for one queue cycle.
     *
     * @param cycle lifecycle cycle to measure
     * @return elapsed duration, or empty while the cycle remains unclaimed
     */
    public static Optional<Duration> timeToClaim(ResolutionCycle cycle) {
        Objects.requireNonNull(cycle, "cycle");
        return cycle.firstAssignedAt().map(firstAssignedAt -> Duration.between(cycle.queueEnteredAt(), firstAssignedAt));
    }

    /**
     * Returns the elapsed time in progress for one resolved cycle.
     *
     * @param cycle lifecycle cycle to measure
     * @return elapsed duration, or empty while the cycle is unresolved
     */
    public static Optional<Duration> timeInProgress(ResolutionCycle cycle) {
        Objects.requireNonNull(cycle, "cycle");
        if (cycle.firstAssignedAt().isEmpty() || cycle.resolution().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(
                cycle.firstAssignedAt().orElseThrow(),
                cycle.resolution().orElseThrow().resolvedAt()));
    }

    /**
     * Aggregates completed-cycle averages and reopen-rate metrics over the given incident scope.
     *
     * <p>Callers select the scope (category, time period, responder) before calling; this method
     * performs no filtering of its own and uses only persisted lifecycle timestamps.
     *
     * @param incidents incidents already filtered to the desired scope
     * @return deterministic aggregate evaluation, {@link SloEvaluation#empty()} for an empty scope
     */
    public static SloEvaluation evaluate(List<Incident> incidents) {
        Objects.requireNonNull(incidents, "incidents");

        List<Duration> claimDurations = new ArrayList<>();
        List<Duration> progressDurations = new ArrayList<>();
        int resolvedIncidentCount = 0;
        int reopenedIncidentCount = 0;
        int reopenEventCount = 0;

        for (Incident incident : incidents) {
            boolean resolvedAtLeastOnce = false;
            for (ResolutionCycle cycle : incident.resolutionCycles()) {
                timeToClaim(cycle).ifPresent(claimDurations::add);
                timeInProgress(cycle).ifPresent(progressDurations::add);
                if (cycle.isResolved()) {
                    resolvedAtLeastOnce = true;
                }
            }
            if (resolvedAtLeastOnce) {
                resolvedIncidentCount++;
                reopenEventCount += incident.reopenCount();
                if (incident.reopenCount() > 0) {
                    reopenedIncidentCount++;
                }
            }
        }

        Optional<Double> reopenRate = resolvedIncidentCount == 0
                ? Optional.empty()
                : Optional.of((double) reopenedIncidentCount / resolvedIncidentCount);

        return new SloEvaluation(
                average(claimDurations),
                claimDurations.size(),
                average(progressDurations),
                progressDurations.size(),
                reopenRate,
                reopenedIncidentCount,
                resolvedIncidentCount,
                reopenEventCount);
    }

    /**
     * Computes the live SLO badge for an incident's current lifecycle position.
     *
     * @param incident incident to evaluate
     * @param targetVersion configuration version effective for the incident's category, when configured
     * @param now application-generated current instant, never a UI clock
     * @return live status, {@link SloStatusModel#notApplicable()} when no metric currently applies
     */
    public static SloStatusModel liveStatus(Incident incident, Optional<SloTargetVersion> targetVersion, Instant now) {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(targetVersion, "targetVersion");
        Objects.requireNonNull(now, "now");

        if (targetVersion.isEmpty()) {
            return SloStatusModel.notApplicable();
        }
        Optional<ResolutionCycle> currentCycle = incident.currentCycle();
        if (currentCycle.isEmpty()) {
            return SloStatusModel.notApplicable();
        }
        ResolutionCycle cycle = currentCycle.orElseThrow();
        SloTarget target = targetVersion.orElseThrow().target();

        if (incident.status() == IncidentStatus.SUBMITTED && cycle.firstAssignedAt().isEmpty()) {
            Duration elapsed = Duration.between(cycle.queueEnteredAt(), now);
            return SloStatusModel.evaluated(SloLiveMetricType.TIME_TO_CLAIM, elapsed, target.timeToClaimTarget());
        }
        if (incident.status() == IncidentStatus.ASSIGNED) {
            Duration elapsed = Duration.between(cycle.firstAssignedAt().orElseThrow(), now);
            return SloStatusModel.evaluated(SloLiveMetricType.TIME_IN_PROGRESS, elapsed, target.timeInProgressTarget());
        }
        return SloStatusModel.notApplicable();
    }

    private static Optional<Duration> average(List<Duration> durations) {
        if (durations.isEmpty()) {
            return Optional.empty();
        }
        Duration total = Duration.ZERO;
        for (Duration duration : durations) {
            total = total.plus(duration);
        }
        return Optional.of(total.dividedBy(durations.size()));
    }
}

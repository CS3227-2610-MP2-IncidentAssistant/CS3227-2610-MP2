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
     * <p>Callers select the scope (category, responder) before calling; this method performs no
     * filtering of its own and uses only persisted lifecycle timestamps.
     *
     * @param incidents incidents already filtered to the desired scope
     * @return deterministic aggregate evaluation, {@link SloEvaluation#empty()} for an empty scope
     */
    public static SloEvaluation evaluate(List<Incident> incidents) {
        return evaluate(incidents, Optional.empty(), Optional.empty());
    }

    /**
     * Aggregates completed-cycle averages and reopen-rate metrics, restricted to a reporting period.
     *
     * <p>A cycle's time-to-claim contributes only when its queue-entry timestamp falls within the
     * period; its time-in-progress, resolution, and reopen contribution only when its resolution
     * timestamp falls within the period. Both bounds are inclusive. An incident unresolved at the
     * end of the period contributes no resolution-based metrics, without error.
     *
     * @param incidents incidents already filtered to the desired category/responder scope
     * @param periodFrom inclusive period start, or empty for no lower bound
     * @param periodThrough inclusive period end, or empty for no upper bound
     * @return deterministic aggregate evaluation, {@link SloEvaluation#empty()} for an empty scope
     */
    public static SloEvaluation evaluate(
            List<Incident> incidents, Optional<Instant> periodFrom, Optional<Instant> periodThrough) {
        Objects.requireNonNull(incidents, "incidents");
        Objects.requireNonNull(periodFrom, "periodFrom");
        Objects.requireNonNull(periodThrough, "periodThrough");

        PeriodTally tally = new PeriodTally(periodFrom, periodThrough);
        incidents.forEach(tally::add);
        return tally.toEvaluation();
    }

    /** Returns whether the cycle was resolved at an instant within the inclusive period. */
    public static boolean isResolvedInPeriod(
            ResolutionCycle cycle, Optional<Instant> from, Optional<Instant> through) {
        return cycle.resolution()
                .map(resolution -> inPeriod(resolution.resolvedAt(), from, through))
                .orElse(false);
    }

    /**
     * Checks whether an instant falls within an inclusive optional period.
     *
     * @param instant timestamp to test
     * @param from inclusive period start, or empty for no lower bound
     * @param through inclusive period end, or empty for no upper bound
     * @return true when the instant satisfies both bounds
     */
    public static boolean inPeriod(Instant instant, Optional<Instant> from, Optional<Instant> through) {
        return from.map(start -> !instant.isBefore(start)).orElse(true)
                && through.map(end -> !instant.isAfter(end)).orElse(true);
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

    /** Returns the mean of the durations, or empty when there are none. */
    public static Optional<Duration> average(List<Duration> durations) {
        if (durations.isEmpty()) {
            return Optional.empty();
        }
        Duration total = Duration.ZERO;
        for (Duration duration : durations) {
            total = total.plus(duration);
        }
        return Optional.of(total.dividedBy(durations.size()));
    }

    /** Accumulates period-restricted claim, progress, and reopen figures across incidents. */
    private static final class PeriodTally {
        private final Optional<Instant> periodFrom;
        private final Optional<Instant> periodThrough;
        private final List<Duration> claimDurations = new ArrayList<>();
        private final List<Duration> progressDurations = new ArrayList<>();
        private int resolvedIncidentCount;
        private int reopenedIncidentCount;
        private int reopenEventCount;

        private PeriodTally(Optional<Instant> periodFrom, Optional<Instant> periodThrough) {
            this.periodFrom = periodFrom;
            this.periodThrough = periodThrough;
        }

        private void add(Incident incident) {
            List<ResolutionCycle> cycles = incident.resolutionCycles();
            int reopenEventsInPeriod = 0;
            boolean resolvedInPeriod = false;
            for (int index = 0; index < cycles.size(); index++) {
                ResolutionCycle cycle = cycles.get(index);
                if (inPeriod(cycle.queueEnteredAt(), periodFrom, periodThrough)) {
                    timeToClaim(cycle).ifPresent(claimDurations::add);
                }
                if (!isResolvedInPeriod(cycle, periodFrom, periodThrough)) {
                    continue;
                }
                timeInProgress(cycle).ifPresent(progressDurations::add);
                resolvedInPeriod = true;
                boolean reopened = index + 1 < cycles.size();
                if (reopened) {
                    reopenEventsInPeriod++;
                }
            }
            reopenEventCount += reopenEventsInPeriod;
            if (resolvedInPeriod) {
                resolvedIncidentCount++;
            }
            if (reopenEventsInPeriod > 0) {
                reopenedIncidentCount++;
            }
        }

        private SloEvaluation toEvaluation() {
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
    }
}

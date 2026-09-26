package com.company.incidentdesk.domain.statistics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.Resolution;
import com.company.incidentdesk.domain.incident.ResolutionCycle;
import com.company.incidentdesk.domain.slo.SloCalculator;
import com.company.incidentdesk.domain.slo.SloEvaluation;

/** Pure, deterministic operational-statistics aggregation over persisted lifecycle timestamps. */
public final class IncidentStatisticsCalculator {
    private IncidentStatisticsCalculator() {
    }

    /**
     * Aggregates incident-perspective statistics for a category/period scope.
     *
     * @param incidents incidents already filtered to the desired category/reporter scope
     * @param periodFrom inclusive period start, or empty for no lower bound
     * @param periodThrough inclusive period end, or empty for no upper bound
     * @return deterministic summary, {@link StatisticsSummary#empty()} for an empty scope
     */
    public static StatisticsSummary summarize(
            List<Incident> incidents, Optional<Instant> periodFrom, Optional<Instant> periodThrough) {
        Objects.requireNonNull(incidents, "incidents");
        Objects.requireNonNull(periodFrom, "periodFrom");
        Objects.requireNonNull(periodThrough, "periodThrough");

        SloEvaluation aggregate = SloCalculator.evaluate(incidents, periodFrom, periodThrough);
        int administratorResolvedCount = 0;
        for (Incident incident : incidents) {
            for (ResolutionCycle cycle : incident.resolutionCycles()) {
                if (isAdministratorResolvedInPeriod(cycle, periodFrom, periodThrough)) {
                    administratorResolvedCount++;
                }
            }
        }
        return new StatisticsSummary(aggregate, administratorResolvedCount);
    }

    /**
     * Groups resolution-event statistics by the responder assigned when each resolution occurred.
     *
     * <p>Only resolved cycles whose resolution falls within the period contribute. Results are
     * sorted deterministically by responder identifier; callers attach display names and any
     * further ordering in the presentation layer.
     *
     * @param incidents incidents already filtered to the desired category/reporter scope
     * @param periodFrom inclusive period start, or empty for no lower bound
     * @param periodThrough inclusive period end, or empty for no upper bound
     * @return one entry per responder with at least one resolution event in scope
     */
    public static List<ResponderStatistics> byResponder(
            List<Incident> incidents, Optional<Instant> periodFrom, Optional<Instant> periodThrough) {
        Objects.requireNonNull(incidents, "incidents");
        Objects.requireNonNull(periodFrom, "periodFrom");
        Objects.requireNonNull(periodThrough, "periodThrough");

        Map<AccountId, List<Duration>> progressByResponder = new LinkedHashMap<>();
        Map<AccountId, Integer> reopenedByResponder = new LinkedHashMap<>();
        Map<AccountId, Integer> administratorResolvedByResponder = new LinkedHashMap<>();

        for (Incident incident : incidents) {
            List<ResolutionCycle> cycles = incident.resolutionCycles();
            for (int index = 0; index < cycles.size(); index++) {
                ResolutionCycle cycle = cycles.get(index);
                if (!cycle.isResolved()) {
                    continue;
                }
                Resolution resolution = cycle.resolution().orElseThrow();
                if (!SloCalculator.inPeriod(resolution.resolvedAt(), periodFrom, periodThrough)) {
                    continue;
                }
                AccountId responderId = resolution.responderAtResolution();
                List<Duration> progressDurations = progressByResponder.computeIfAbsent(
                        responderId, id -> new ArrayList<>());
                SloCalculator.timeInProgress(cycle).ifPresent(progressDurations::add);
                if (index + 1 < cycles.size()) {
                    reopenedByResponder.merge(responderId, 1, Integer::sum);
                }
                if (!resolution.resolvedBy().equals(responderId)) {
                    administratorResolvedByResponder.merge(responderId, 1, Integer::sum);
                }
            }
        }

        return progressByResponder.entrySet().stream()
                .map(entry -> toResponderStatistics(
                        entry.getKey(),
                        entry.getValue(),
                        reopenedByResponder.getOrDefault(entry.getKey(), 0),
                        administratorResolvedByResponder.getOrDefault(entry.getKey(), 0)))
                .sorted(Comparator.comparing(statistics -> statistics.responderId().value()))
                .toList();
    }

    private static ResponderStatistics toResponderStatistics(
            AccountId responderId,
            List<Duration> progressDurations,
            int reopenedCycleCount,
            int administratorResolvedCount) {
        int resolvedCycleCount = progressDurations.size();
        Optional<Double> reopenRate = resolvedCycleCount == 0
                ? Optional.empty()
                : Optional.of((double) reopenedCycleCount / resolvedCycleCount);
        return new ResponderStatistics(
                responderId,
                resolvedCycleCount,
                SloCalculator.average(progressDurations),
                reopenedCycleCount,
                reopenRate,
                administratorResolvedCount);
    }

    private static boolean isAdministratorResolvedInPeriod(
            ResolutionCycle cycle, Optional<Instant> from, Optional<Instant> through) {
        if (!cycle.isResolved()) {
            return false;
        }
        Resolution resolution = cycle.resolution().orElseThrow();
        return SloCalculator.inPeriod(resolution.resolvedAt(), from, through)
                && !resolution.resolvedBy().equals(resolution.responderAtResolution());
    }
}

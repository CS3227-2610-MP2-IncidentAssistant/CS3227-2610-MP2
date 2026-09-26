package com.company.incidentdesk.domain.statistics;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.account.AccountId;

/**
 * Deterministic per-responder operational statistics attributed by resolution event.
 *
 * <p>Counts resolution <em>events</em> (resolved cycles), not distinct incidents: a responder who
 * resolves the same incident twice across a reopen contributes two resolved cycles, consistent with
 * the per-resolution attribution rule in {@code .agents/slo.md}. Time-to-claim is deliberately
 * excluded: it measures how quickly the queue was worked, which is a category/incident-level
 * concept, not a property of whichever responder ultimately resolved the cycle.
 */
public record ResponderStatistics(
        AccountId responderId,
        int resolvedCycleCount,
        Optional<Duration> averageTimeInProgress,
        int reopenedCycleCount,
        Optional<Double> reopenRate,
        int administratorResolvedCount) {
    public ResponderStatistics {
        Objects.requireNonNull(responderId, "responderId");
        Objects.requireNonNull(averageTimeInProgress, "averageTimeInProgress");
        Objects.requireNonNull(reopenRate, "reopenRate");
        requireNonNegative(resolvedCycleCount, "resolvedCycleCount");
        requireNonNegative(reopenedCycleCount, "reopenedCycleCount");
        requireNonNegative(administratorResolvedCount, "administratorResolvedCount");
        if (averageTimeInProgress.isPresent() != (resolvedCycleCount > 0)) {
            throw new IllegalArgumentException("averageTimeInProgress must be present only with resolved cycles");
        }
        if (reopenRate.isPresent() != (resolvedCycleCount > 0)) {
            throw new IllegalArgumentException("reopenRate must be present only with resolved cycles");
        }
        if (reopenedCycleCount > resolvedCycleCount) {
            throw new IllegalArgumentException("reopenedCycleCount cannot exceed resolvedCycleCount");
        }
        if (administratorResolvedCount > resolvedCycleCount) {
            throw new IllegalArgumentException("administratorResolvedCount cannot exceed resolvedCycleCount");
        }
        reopenRate.ifPresent(rate -> {
            if (!Double.isFinite(rate) || rate < 0 || rate > 1) {
                throw new IllegalArgumentException("reopenRate must be between 0 and 1");
            }
        });
    }

    /** Returns the deterministic empty statistics for a responder with no resolutions in scope. */
    public static ResponderStatistics empty(AccountId responderId) {
        return new ResponderStatistics(responderId, 0, Optional.empty(), 0, Optional.empty(), 0);
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}

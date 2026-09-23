package com.company.incidentdesk.domain.slo;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SloEvaluationTest {
    @Test
    void rejectsAnAverageWithoutAMatchingCount() {
        assertThrows(IllegalArgumentException.class, () -> new SloEvaluation(
                Optional.of(Duration.ZERO), 0, Optional.empty(), 0, Optional.empty(), 0, 0, 0));
    }

    @Test
    void rejectsACountWithoutAMatchingAverage() {
        assertThrows(IllegalArgumentException.class, () -> new SloEvaluation(
                Optional.empty(), 1, Optional.empty(), 0, Optional.empty(), 0, 0, 0));
    }

    @Test
    void rejectsMoreReopenedIncidentsThanResolvedIncidents() {
        assertThrows(IllegalArgumentException.class, () -> new SloEvaluation(
                Optional.empty(), 0, Optional.empty(), 0, Optional.of(1.0), 2, 1, 2));
    }

    @Test
    void rejectsAReopenRateOutsideZeroToOne() {
        assertThrows(IllegalArgumentException.class, () -> new SloEvaluation(
                Optional.empty(), 0, Optional.empty(), 0, Optional.of(1.5), 1, 1, 1));
    }

    @Test
    void emptyEvaluationHasNoAveragesOrRate() {
        SloEvaluation evaluation = SloEvaluation.empty();
        assertThrows(IllegalArgumentException.class, () -> new SloEvaluation(
                Optional.of(Duration.ZERO), 0,
                evaluation.averageTimeInProgress(), evaluation.completedCycleCount(),
                evaluation.reopenRate(), evaluation.reopenedIncidentCount(),
                evaluation.resolvedIncidentCount(), evaluation.reopenEventCount()));
    }
}

package com.company.incidentdesk.domain.slo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SloStatusModelTest {
    @Test
    void evaluatedAtExactlyTheTargetIsWithinTarget() {
        SloStatusModel status = SloStatusModel.evaluated(
                SloLiveMetricType.TIME_TO_CLAIM, Duration.ofMinutes(30), Duration.ofMinutes(30));
        assertEquals(SloComplianceState.WITHIN_TARGET, status.state());
    }

    @Test
    void evaluatedPastTheTargetIsOverdue() {
        SloStatusModel status = SloStatusModel.evaluated(
                SloLiveMetricType.TIME_TO_CLAIM, Duration.ofMinutes(31), Duration.ofMinutes(30));
        assertEquals(SloComplianceState.OVERDUE, status.state());
    }

    @Test
    void notApplicableCarriesNoMetricDetails() {
        SloStatusModel status = SloStatusModel.notApplicable();
        assertEquals(SloComplianceState.NOT_APPLICABLE, status.state());
        assertEquals(true, status.metric().isEmpty());
        assertEquals(true, status.elapsed().isEmpty());
        assertEquals(true, status.target().isEmpty());
    }

    @Test
    void rejectsAnEvaluatedStateWithoutMetricDetails() {
        assertThrows(IllegalArgumentException.class, () -> new SloStatusModel(
                SloComplianceState.WITHIN_TARGET,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.empty()));
    }

    @Test
    void rejectsANotApplicableStateCarryingMetricDetails() {
        assertThrows(IllegalArgumentException.class, () -> new SloStatusModel(
                SloComplianceState.NOT_APPLICABLE,
                java.util.Optional.of(SloLiveMetricType.TIME_TO_CLAIM),
                java.util.Optional.of(Duration.ZERO),
                java.util.Optional.of(Duration.ZERO)));
    }
}

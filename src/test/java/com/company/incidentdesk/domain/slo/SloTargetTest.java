package com.company.incidentdesk.domain.slo;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SloTargetTest {
    @Test
    void rejectsNegativeTimeToClaimTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> new SloTarget(Duration.ofSeconds(-1), Duration.ZERO, 0.1));
    }

    @Test
    void rejectsNegativeTimeInProgressTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> new SloTarget(Duration.ZERO, Duration.ofSeconds(-1), 0.1));
    }

    @Test
    void rejectsReopenRateOutsideZeroToOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new SloTarget(Duration.ZERO, Duration.ZERO, 1.1));
        assertThrows(IllegalArgumentException.class,
                () -> new SloTarget(Duration.ZERO, Duration.ZERO, -0.1));
        assertThrows(IllegalArgumentException.class,
                () -> new SloTarget(Duration.ZERO, Duration.ZERO, Double.NaN));
    }

    @Test
    void acceptsZeroDurationsAndBoundaryReopenRates() {
        new SloTarget(Duration.ZERO, Duration.ZERO, 0.0);
        new SloTarget(Duration.ZERO, Duration.ZERO, 1.0);
    }
}

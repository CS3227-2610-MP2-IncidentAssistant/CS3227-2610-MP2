package com.company.incidentdesk.domain.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;

class ResponderStatisticsTest {
    private static final AccountId RESPONDER_ID = new AccountId(new UUID(0, 1));

    @Test
    void rejectsAnAverageWithoutResolvedCycles() {
        assertThrows(IllegalArgumentException.class, () -> new ResponderStatistics(
                RESPONDER_ID, 0, Optional.of(Duration.ZERO), 0, Optional.empty(), 0));
    }

    @Test
    void rejectsMoreReopenedCyclesThanResolvedCycles() {
        assertThrows(IllegalArgumentException.class, () -> new ResponderStatistics(
                RESPONDER_ID, 1, Optional.of(Duration.ZERO), 2, Optional.of(1.0), 0));
    }

    @Test
    void rejectsMoreAdministratorResolvedCyclesThanResolvedCycles() {
        assertThrows(IllegalArgumentException.class, () -> new ResponderStatistics(
                RESPONDER_ID, 1, Optional.of(Duration.ZERO), 0, Optional.of(0.0), 2));
    }

    @Test
    void rejectsAReopenRateOutsideZeroToOne() {
        assertThrows(IllegalArgumentException.class, () -> new ResponderStatistics(
                RESPONDER_ID, 1, Optional.of(Duration.ZERO), 1, Optional.of(1.5), 0));
    }

    @Test
    void emptyStatisticsHasNoAveragesOrRate() {
        ResponderStatistics empty = ResponderStatistics.empty(RESPONDER_ID);
        assertEquals(0, empty.resolvedCycleCount());
        assertEquals(Optional.empty(), empty.averageTimeInProgress());
        assertEquals(Optional.empty(), empty.reopenRate());
    }
}

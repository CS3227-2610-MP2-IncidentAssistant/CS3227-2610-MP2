package com.company.incidentdesk.domain.incident;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;

/** Tests assignment and resolution timestamp invariants within a cycle. */
class ResolutionCycleTest {
    private static final Instant QUEUED_AT = Instant.parse("2026-09-19T08:01:00Z");
    private static final Instant FIRST_ASSIGNED_AT = Instant.parse("2026-09-19T08:02:00Z");
    private static final Instant LATEST_ASSIGNED_AT = Instant.parse("2026-09-19T08:03:00Z");
    private static final Instant RESOLVED_AT = Instant.parse("2026-09-19T08:04:00Z");
    private static final AccountId RESPONDER_ID = new AccountId(
            UUID.fromString("0b9cbec9-d5ae-4532-85b5-66ea7b4afe83"));

    @Test
    void acceptsEqualTimestampsForZeroDurationCycle() {
        Resolution resolution = resolutionAt(QUEUED_AT);

        assertDoesNotThrow(() -> new ResolutionCycle(
                QUEUED_AT,
                Optional.of(QUEUED_AT),
                Optional.of(QUEUED_AT),
                Optional.of(resolution)));
    }

    @Test
    void rejectsFirstAssignmentBeforeQueueEntry() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolutionCycle(
                        QUEUED_AT,
                        Optional.of(QUEUED_AT.minusSeconds(1)),
                        Optional.of(LATEST_ASSIGNED_AT),
                        Optional.empty()));
    }

    @Test
    void rejectsLatestAssignmentWithoutFirstAssignment() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolutionCycle(
                        QUEUED_AT,
                        Optional.empty(),
                        Optional.of(LATEST_ASSIGNED_AT),
                        Optional.empty()));
    }

    @Test
    void rejectsFirstAssignmentWithoutLatestAssignment() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolutionCycle(
                        QUEUED_AT,
                        Optional.of(FIRST_ASSIGNED_AT),
                        Optional.empty(),
                        Optional.empty()));
    }

    @Test
    void rejectsLatestAssignmentBeforeFirstAssignment() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolutionCycle(
                        QUEUED_AT,
                        Optional.of(FIRST_ASSIGNED_AT),
                        Optional.of(FIRST_ASSIGNED_AT.minusSeconds(1)),
                        Optional.empty()));
    }

    @Test
    void rejectsResolutionWithoutAssignment() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolutionCycle(
                        QUEUED_AT,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(resolutionAt(RESOLVED_AT))));
    }

    @Test
    void rejectsResolutionBeforeLatestAssignment() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolutionCycle(
                        QUEUED_AT,
                        Optional.of(FIRST_ASSIGNED_AT),
                        Optional.of(LATEST_ASSIGNED_AT),
                        Optional.of(resolutionAt(LATEST_ASSIGNED_AT.minusSeconds(1)))));
    }

    private Resolution resolutionAt(Instant resolvedAt) {
        return new Resolution("Resolved", resolvedAt, RESPONDER_ID, RESPONDER_ID);
    }
}

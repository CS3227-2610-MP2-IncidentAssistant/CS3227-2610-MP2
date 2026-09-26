package com.company.incidentdesk.domain.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.domain.incident.ReopenTransition;

/** Verifies operational-statistics aggregation and the required edge cases from .agents/slo.md. */
class IncidentStatisticsCalculatorTest {
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final AccountId REPORTER_ID = accountId(1);
    private static final AccountId RESPONDER_ID = accountId(2);
    private static final AccountId OTHER_RESPONDER_ID = accountId(3);
    private static final AccountId ADMIN_ID = accountId(4);

    @Test
    void summarizeOfAnEmptyScopeIsDeterministicallyEmpty() {
        assertEquals(StatisticsSummary.empty(),
                IncidentStatisticsCalculator.summarize(List.of(), Optional.empty(), Optional.empty()));
        assertEquals(List.of(),
                IncidentStatisticsCalculator.byResponder(List.of(), Optional.empty(), Optional.empty()));
    }

    @Test
    void byResponderAttributesResolutionToTheResponderAssignedAtResolutionDespiteHandoffsAndReassignments() {
        IncidentId id = new IncidentId(uuid(1));
        Instant submittedAt = BASE;
        Instant firstClaimAt = submittedAt.plusSeconds(10);
        Instant handoffAt = firstClaimAt.plusSeconds(20);
        Instant secondClaimAt = handoffAt.plusSeconds(5);
        Instant reassignedAt = secondClaimAt.plusSeconds(15);
        Instant resolvedAt = reassignedAt.plusSeconds(40);

        Incident incident = lifecycleAt(submittedAt).submit(id, REPORTER_ID, "Title", "Description",
                IncidentCategory.IT, false);
        incident = lifecycleAt(firstClaimAt).claim(incident, RESPONDER_ID);
        incident = lifecycleAt(handoffAt).handoff(incident);
        incident = lifecycleAt(secondClaimAt).claim(incident, RESPONDER_ID);
        incident = lifecycleAt(reassignedAt).reassign(incident, OTHER_RESPONDER_ID);
        incident = lifecycleAt(resolvedAt).resolve(incident, OTHER_RESPONDER_ID, "Fixed");

        List<ResponderStatistics> byResponder = IncidentStatisticsCalculator.byResponder(
                List.of(incident), Optional.empty(), Optional.empty());

        assertEquals(1, byResponder.size());
        ResponderStatistics stats = byResponder.get(0);
        assertEquals(OTHER_RESPONDER_ID, stats.responderId());
        assertEquals(1, stats.resolvedCycleCount());
        assertEquals(Duration.between(firstClaimAt, resolvedAt), stats.averageTimeInProgress().orElseThrow());
        assertEquals(0, stats.reopenedCycleCount());
        assertEquals(Optional.of(0.0), stats.reopenRate());
        assertEquals(0, stats.administratorResolvedCount());
    }

    @Test
    void byResponderAttributesReopenToTheResponderOfTheImmediatelyPrecedingResolution() {
        IncidentId id = new IncidentId(uuid(2));
        Instant submittedAt = BASE;
        Instant claimedAt = submittedAt.plusSeconds(5);
        Instant resolvedAt = claimedAt.plusSeconds(60);
        Instant reopenedAt = resolvedAt.plusSeconds(3600);
        Instant secondClaimAt = reopenedAt.plusSeconds(120);
        Instant secondResolvedAt = secondClaimAt.plusSeconds(90);

        Incident incident = lifecycleAt(submittedAt).submit(id, REPORTER_ID, "Title", "Description",
                IncidentCategory.IT, false);
        incident = lifecycleAt(claimedAt).claim(incident, RESPONDER_ID);
        incident = lifecycleAt(resolvedAt).resolve(incident, RESPONDER_ID, "Fixed");
        ReopenTransition reopenTransition = lifecycleAt(reopenedAt).reopen(incident, REPORTER_ID, "Not fixed");
        incident = reopenTransition.incident();
        incident = lifecycleAt(secondClaimAt).claim(incident, OTHER_RESPONDER_ID);
        incident = lifecycleAt(secondResolvedAt).resolve(incident, OTHER_RESPONDER_ID, "Actually fixed");

        List<ResponderStatistics> byResponder = IncidentStatisticsCalculator.byResponder(
                List.of(incident), Optional.empty(), Optional.empty());

        assertEquals(2, byResponder.size());
        ResponderStatistics first = statsFor(byResponder, RESPONDER_ID);
        ResponderStatistics second = statsFor(byResponder, OTHER_RESPONDER_ID);
        assertEquals(1, first.resolvedCycleCount());
        assertEquals(1, first.reopenedCycleCount());
        assertEquals(Optional.of(1.0), first.reopenRate());
        assertEquals(1, second.resolvedCycleCount());
        assertEquals(0, second.reopenedCycleCount());
        assertEquals(Optional.of(0.0), second.reopenRate());
    }

    @Test
    void summarizeReportsAdministratorResolvedCasesSeparatelyWhileStillAttributingToTheResponder() {
        IncidentId id = new IncidentId(uuid(3));
        Instant submittedAt = BASE;
        Instant claimedAt = submittedAt.plusSeconds(5);
        Instant resolvedAt = claimedAt.plusSeconds(60);

        Incident incident = lifecycleAt(submittedAt).submit(id, REPORTER_ID, "Title", "Description",
                IncidentCategory.IT, false);
        incident = lifecycleAt(claimedAt).claim(incident, RESPONDER_ID);
        incident = lifecycleAt(resolvedAt).resolve(incident, ADMIN_ID, "Administrator override");

        StatisticsSummary summary = IncidentStatisticsCalculator.summarize(
                List.of(incident), Optional.empty(), Optional.empty());
        List<ResponderStatistics> byResponder = IncidentStatisticsCalculator.byResponder(
                List.of(incident), Optional.empty(), Optional.empty());

        assertEquals(1, summary.administratorResolvedCount());
        assertEquals(1, summary.aggregate().completedCycleCount());
        assertEquals(1, byResponder.size());
        assertEquals(RESPONDER_ID, byResponder.get(0).responderId());
        assertEquals(1, byResponder.get(0).administratorResolvedCount());
    }

    @Test
    void summarizeExcludesIncidentsUnresolvedAtTheEndOfTheReportingPeriod() {
        Incident stillAssigned = lifecycleAt(BASE.plusSeconds(1)).claim(
                lifecycleAt(BASE).submit(new IncidentId(uuid(4)), REPORTER_ID, "Title", "Description",
                        IncidentCategory.IT, false),
                RESPONDER_ID);

        StatisticsSummary summary = IncidentStatisticsCalculator.summarize(
                List.of(stillAssigned), Optional.empty(), Optional.of(BASE.plusSeconds(2)));

        assertEquals(0, summary.aggregate().resolvedIncidentCount());
        assertEquals(Optional.empty(), summary.aggregate().reopenRate());
        assertEquals(0, summary.administratorResolvedCount());
        assertTrue(IncidentStatisticsCalculator.byResponder(
                List.of(stillAssigned), Optional.empty(), Optional.of(BASE.plusSeconds(2))).isEmpty());
    }

    @Test
    void summarizeExcludesResolutionsOutsideTheReportingPeriodButKeepsResolutionsInside() {
        Instant claimedAt = BASE.plusSeconds(5);
        Instant resolvedAt = claimedAt.plusSeconds(60);
        Incident incident = lifecycleAt(resolvedAt).resolve(
                lifecycleAt(claimedAt).claim(
                        lifecycleAt(BASE).submit(new IncidentId(uuid(5)), REPORTER_ID, "Title", "Description",
                                IncidentCategory.IT, false),
                        RESPONDER_ID),
                RESPONDER_ID, "Fixed");

        StatisticsSummary outsidePeriod = IncidentStatisticsCalculator.summarize(
                List.of(incident), Optional.empty(), Optional.of(resolvedAt.minusSeconds(1)));
        StatisticsSummary insidePeriod = IncidentStatisticsCalculator.summarize(
                List.of(incident), Optional.of(resolvedAt), Optional.of(resolvedAt));

        assertEquals(0, outsidePeriod.aggregate().resolvedIncidentCount());
        assertEquals(1, insidePeriod.aggregate().resolvedIncidentCount());
    }

    @Test
    void byResponderIsSortedDeterministicallyByResponderIdentifier() {
        Incident first = resolvedIncident(uuid(6), accountId(50), BASE);
        Incident second = resolvedIncident(uuid(7), accountId(10), BASE.plusSeconds(200));

        List<ResponderStatistics> byResponder = IncidentStatisticsCalculator.byResponder(
                List.of(first, second), Optional.empty(), Optional.empty());

        assertEquals(accountId(10), byResponder.get(0).responderId());
        assertEquals(accountId(50), byResponder.get(1).responderId());
    }

    private static Incident resolvedIncident(UUID incidentUuid, AccountId responderId, Instant submittedAt) {
        Instant claimedAt = submittedAt.plusSeconds(5);
        Instant resolvedAt = claimedAt.plusSeconds(30);
        Incident incident = lifecycleAt(submittedAt).submit(new IncidentId(incidentUuid), REPORTER_ID, "Title",
                "Description", IncidentCategory.IT, false);
        incident = lifecycleAt(claimedAt).claim(incident, responderId);
        return lifecycleAt(resolvedAt).resolve(incident, responderId, "Fixed");
    }

    private static ResponderStatistics statsFor(List<ResponderStatistics> statistics, AccountId responderId) {
        return statistics.stream()
                .filter(entry -> entry.responderId().equals(responderId))
                .findFirst()
                .orElseThrow();
    }

    private static IncidentLifecycle lifecycleAt(Instant instant) {
        return new IncidentLifecycle(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static AccountId accountId(long value) {
        return new AccountId(uuid(value));
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}

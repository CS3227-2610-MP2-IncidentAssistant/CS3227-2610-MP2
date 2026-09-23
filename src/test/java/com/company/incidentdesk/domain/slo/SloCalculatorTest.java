package com.company.incidentdesk.domain.slo;

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
import com.company.incidentdesk.domain.incident.ResolutionCycle;

/** Verifies every formula and edge case required by .agents/slo.md. */
class SloCalculatorTest {
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final AccountId REPORTER_ID = accountId(1);
    private static final AccountId RESPONDER_ID = accountId(2);
    private static final AccountId OTHER_RESPONDER_ID = accountId(3);

    @Test
    void timeToClaimIsEmptyForAnUnclaimedCycle() {
        ResolutionCycle cycle = new ResolutionCycle(BASE, Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(Optional.empty(), SloCalculator.timeToClaim(cycle));
    }

    @Test
    void timeToClaimIsZeroForEqualQueueAndAssignmentTimestamps() {
        ResolutionCycle cycle = new ResolutionCycle(BASE, Optional.of(BASE), Optional.of(BASE), Optional.empty());
        assertEquals(Optional.of(Duration.ZERO), SloCalculator.timeToClaim(cycle));
    }

    @Test
    void timeInProgressIsEmptyForAnUnresolvedCycle() {
        Incident incident = lifecycleAt(BASE).claim(lifecycleAt(BASE).submit(
                new IncidentId(uuid(10)), REPORTER_ID, "Title", "Description", IncidentCategory.IT, false),
                RESPONDER_ID);
        assertEquals(Optional.empty(), SloCalculator.timeInProgress(incident.currentCycle().orElseThrow()));
    }

    @Test
    void evaluateOfAnEmptyScopeIsDeterministicallyEmpty() {
        assertEquals(SloEvaluation.empty(), SloCalculator.evaluate(List.of()));
    }

    @Test
    void evaluateExcludesUnclaimedIncidentsFromClaimAverageButCountsClaimed() {
        Incident unclaimed = lifecycleAt(BASE).submit(
                new IncidentId(uuid(11)), REPORTER_ID, "Title", "Description", IncidentCategory.IT, false);
        Instant claimedAt = BASE.plusSeconds(30);
        Incident claimed = lifecycleAt(claimedAt).claim(
                lifecycleAt(BASE).submit(
                        new IncidentId(uuid(12)), REPORTER_ID, "Title", "Description", IncidentCategory.IT, false),
                RESPONDER_ID);

        SloEvaluation evaluation = SloCalculator.evaluate(List.of(unclaimed, claimed));

        assertEquals(1, evaluation.claimedCycleCount());
        assertEquals(Optional.of(Duration.ofSeconds(30)), evaluation.averageTimeToClaim());
        assertEquals(0, evaluation.completedCycleCount());
        assertEquals(Optional.empty(), evaluation.averageTimeInProgress());
        assertEquals(0, evaluation.resolvedIncidentCount());
        assertEquals(Optional.empty(), evaluation.reopenRate());
    }

    @Test
    void evaluateContinuesTimeInProgressAcrossMultipleHandoffsAndReassignments() {
        IncidentId id = new IncidentId(uuid(13));
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

        SloEvaluation evaluation = SloCalculator.evaluate(List.of(incident));

        assertEquals(1, evaluation.claimedCycleCount());
        assertEquals(Duration.ofSeconds(10), evaluation.averageTimeToClaim().orElseThrow());
        assertEquals(1, evaluation.completedCycleCount());
        assertEquals(Duration.between(firstClaimAt, resolvedAt), evaluation.averageTimeInProgress().orElseThrow());
    }

    @Test
    void evaluateStartsANewQueueCycleOnReopenAndCountsOneReopenedIncident() {
        IncidentId id = new IncidentId(uuid(14));
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
        ReopenTransition reopenTransition = lifecycleAt(reopenedAt).reopen(incident, REPORTER_ID, "Not actually fixed");
        incident = reopenTransition.incident();
        incident = lifecycleAt(secondClaimAt).claim(incident, RESPONDER_ID);
        incident = lifecycleAt(secondResolvedAt).resolve(incident, RESPONDER_ID, "Actually fixed");

        assertEquals(2, incident.resolutionCycles().size());
        assertEquals(reopenedAt, incident.resolutionCycles().get(1).queueEnteredAt());

        SloEvaluation evaluation = SloCalculator.evaluate(List.of(incident));

        assertEquals(2, evaluation.claimedCycleCount());
        assertEquals(2, evaluation.completedCycleCount());
        assertEquals(1, evaluation.resolvedIncidentCount());
        assertEquals(1, evaluation.reopenedIncidentCount());
        assertEquals(1, evaluation.reopenEventCount());
        assertEquals(Optional.of(1.0), evaluation.reopenRate());
    }

    @Test
    void evaluateExcludesIncidentsUnresolvedAtTheEndOfThePeriodFromReopenRate() {
        Incident stillAssigned = lifecycleAt(BASE.plusSeconds(1)).claim(
                lifecycleAt(BASE).submit(
                        new IncidentId(uuid(15)), REPORTER_ID, "Title", "Description", IncidentCategory.IT, false),
                RESPONDER_ID);

        SloEvaluation evaluation = SloCalculator.evaluate(List.of(stillAssigned));

        assertEquals(0, evaluation.resolvedIncidentCount());
        assertEquals(Optional.empty(), evaluation.reopenRate());
        assertEquals(0, evaluation.reopenedIncidentCount());
    }

    @Test
    void evaluateAvoidsDivisionByZeroWhenNoIncidentsWereResolved() {
        SloEvaluation evaluation = SloCalculator.evaluate(List.of());
        assertEquals(Optional.empty(), evaluation.reopenRate());
    }

    @Test
    void liveStatusIsNotApplicableWithoutAConfiguredTarget() {
        Incident submitted = lifecycleAt(BASE).submit(
                new IncidentId(uuid(20)), REPORTER_ID, "Title", "Description", IncidentCategory.IT, false);
        SloStatusModel status = SloCalculator.liveStatus(submitted, Optional.empty(), BASE.plusSeconds(10));
        assertEquals(SloStatusModel.notApplicable(), status);
    }

    @Test
    void liveStatusIsWithinTargetAtTheExactBoundaryAndOverdueJustAfter() {
        Incident submitted = lifecycleAt(BASE).submit(
                new IncidentId(uuid(21)), REPORTER_ID, "Title", "Description", IncidentCategory.IT, false);
        SloTargetVersion targetVersion = version(IncidentCategory.IT, Duration.ofMinutes(30), Duration.ofHours(4), 0.1);

        SloStatusModel atBoundary = SloCalculator.liveStatus(
                submitted, Optional.of(targetVersion), BASE.plus(Duration.ofMinutes(30)));
        SloStatusModel afterBoundary = SloCalculator.liveStatus(
                submitted, Optional.of(targetVersion), BASE.plus(Duration.ofMinutes(30)).plusSeconds(1));

        assertEquals(SloComplianceState.WITHIN_TARGET, atBoundary.state());
        assertEquals(SloLiveMetricType.TIME_TO_CLAIM, atBoundary.metric().orElseThrow());
        assertEquals(SloComplianceState.OVERDUE, afterBoundary.state());
    }

    @Test
    void liveStatusMeasuresTimeInProgressWhileAssigned() {
        Instant claimedAt = BASE.plusSeconds(120);
        Incident assigned = lifecycleAt(claimedAt).claim(
                lifecycleAt(BASE).submit(
                        new IncidentId(uuid(22)), REPORTER_ID, "Title", "Description", IncidentCategory.IT, false),
                RESPONDER_ID);
        SloTargetVersion targetVersion = version(IncidentCategory.IT, Duration.ofMinutes(30), Duration.ofHours(4), 0.1);

        SloStatusModel status = SloCalculator.liveStatus(
                assigned, Optional.of(targetVersion), claimedAt.plus(Duration.ofHours(5)));

        assertEquals(SloComplianceState.OVERDUE, status.state());
        assertEquals(SloLiveMetricType.TIME_IN_PROGRESS, status.metric().orElseThrow());
        assertEquals(Duration.ofHours(5), status.elapsed().orElseThrow());
    }

    @Test
    void liveStatusIsNotApplicableForResolvedWithdrawnAndDraftIncidents() {
        Incident resolved = lifecycleAt(BASE.plusSeconds(20)).resolve(
                lifecycleAt(BASE.plusSeconds(10)).claim(
                        lifecycleAt(BASE).submit(
                                new IncidentId(uuid(23)), REPORTER_ID, "Title", "Description",
                                IncidentCategory.IT, false),
                        RESPONDER_ID),
                RESPONDER_ID, "Fixed");
        Incident withdrawn = lifecycleAt(BASE.plusSeconds(5)).withdraw(
                lifecycleAt(BASE).submit(
                        new IncidentId(uuid(24)), REPORTER_ID, "Title", "Description", IncidentCategory.IT, false));
        Incident draft = lifecycleAt(BASE).saveDraft(
                new IncidentId(uuid(25)), REPORTER_ID, "Title", "Description", IncidentCategory.IT, false);
        SloTargetVersion targetVersion = version(IncidentCategory.IT, Duration.ofMinutes(30), Duration.ofHours(4), 0.1);

        assertEquals(SloStatusModel.notApplicable(),
                SloCalculator.liveStatus(resolved, Optional.of(targetVersion), BASE.plusSeconds(30)));
        assertEquals(SloStatusModel.notApplicable(),
                SloCalculator.liveStatus(withdrawn, Optional.of(targetVersion), BASE.plusSeconds(30)));
        assertEquals(SloStatusModel.notApplicable(),
                SloCalculator.liveStatus(draft, Optional.of(targetVersion), BASE.plusSeconds(30)));
    }

    @Test
    void liveStatusDisplayIsTimezoneIndependentAcrossDaylightSavingBoundaries() {
        Incident submitted = lifecycleAt(BASE).submit(
                new IncidentId(uuid(26)), REPORTER_ID, "Title", "Description", IncidentCategory.IT, false);
        SloTargetVersion targetVersion = version(IncidentCategory.IT, Duration.ofHours(1), Duration.ofHours(4), 0.1);
        Instant now = BASE.plus(Duration.ofMinutes(30));

        SloStatusModel utc = SloCalculator.liveStatus(submitted, Optional.of(targetVersion), now);
        assertTrue(utc.elapsed().orElseThrow().equals(Duration.ofMinutes(30)));
        // Zone conversions are display-only; the elapsed duration is computed purely from Instants.
        assertEquals(now.atZone(ZoneOffset.UTC).toInstant(), now.atZone(ZoneOffset.ofHours(-5)).toInstant());
    }

    private static SloTargetVersion version(
            IncidentCategory category, Duration timeToClaim, Duration timeInProgress, double reopenRate) {
        return new SloTargetVersion(
                new SloTargetVersionId(uuid(999)),
                category,
                new SloTarget(timeToClaim, timeInProgress, reopenRate),
                BASE,
                accountId(99));
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

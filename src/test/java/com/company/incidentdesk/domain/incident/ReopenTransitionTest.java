package com.company.incidentdesk.domain.incident;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;

/** Tests consistency between a reopened incident and its required explanation. */
class ReopenTransitionTest {
    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-19T08:00:00Z");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-19T08:01:00Z");
    private static final Instant RESOLVED_AT = Instant.parse("2026-09-19T08:02:00Z");
    private static final Instant REOPENED_AT = Instant.parse("2026-09-19T08:03:00Z");
    private static final AccountId ACCOUNT_ID = new AccountId(
            UUID.fromString("f24a7d0a-7c48-4703-b9d7-a39c933149a1"));
    private static final IncidentId INCIDENT_ID = new IncidentId(
            UUID.fromString("61e27e29-7a71-405a-a1a0-24b14e4de0fb"));

    @Test
    void rejectsExplanationForDifferentCycleOrTime() {
        Incident reopened = reopenedIncident();

        assertThrows(
                IllegalArgumentException.class,
                () -> new ReopenTransition(
                        reopened,
                        new ReopenExplanation(ACCOUNT_ID, "Still broken", REOPENED_AT, 3)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReopenTransition(
                        reopened,
                        new ReopenExplanation(
                                ACCOUNT_ID,
                                "Still broken",
                                REOPENED_AT.plusSeconds(1),
                                2)));
    }

    @Test
    void rejectsNonSubmittedIncident() {
        Incident resolved = resolvedIncident();
        ReopenExplanation explanation = new ReopenExplanation(
                ACCOUNT_ID,
                "Still broken",
                REOPENED_AT,
                2);

        assertThrows(IllegalArgumentException.class, () -> new ReopenTransition(resolved, explanation));
    }

    private static Incident reopenedIncident() {
        List<ResolutionCycle> cycles = List.of(
                resolvedCycle(),
                new ResolutionCycle(
                        REOPENED_AT,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()));
        return incident(IncidentStatus.SUBMITTED, cycles);
    }

    private static Incident resolvedIncident() {
        return incident(IncidentStatus.RESOLVED, List.of(resolvedCycle()));
    }

    private static Incident incident(IncidentStatus status, List<ResolutionCycle> cycles) {
        return new Incident(
                INCIDENT_ID,
                ACCOUNT_ID,
                "Incident title",
                "Incident description",
                IncidentCategory.IT,
                status,
                false,
                SUBMITTED_AT,
                Optional.of(SUBMITTED_AT),
                Optional.empty(),
                Optional.empty(),
                cycles);
    }

    private static ResolutionCycle resolvedCycle() {
        Resolution resolution = new Resolution(
                "Resolved",
                RESOLVED_AT,
                ACCOUNT_ID,
                ACCOUNT_ID);
        return new ResolutionCycle(
                SUBMITTED_AT,
                Optional.of(ASSIGNED_AT),
                Optional.of(ASSIGNED_AT),
                Optional.of(resolution));
    }
}

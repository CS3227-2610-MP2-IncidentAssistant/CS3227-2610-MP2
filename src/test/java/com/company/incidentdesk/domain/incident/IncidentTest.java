package com.company.incidentdesk.domain.incident;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;

/** Tests incident content, status, history, and timestamp invariants. */
class IncidentTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-19T08:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-19T08:01:00Z");
    private static final Instant FIRST_ASSIGNED_AT = Instant.parse("2026-09-19T08:02:00Z");
    private static final Instant LATEST_ASSIGNED_AT = Instant.parse("2026-09-19T08:03:00Z");
    private static final Instant RESOLVED_AT = Instant.parse("2026-09-19T08:04:00Z");
    private static final Instant REOPENED_AT = Instant.parse("2026-09-19T08:05:00Z");
    private static final IncidentId INCIDENT_ID = new IncidentId(
            UUID.fromString("61e27e29-7a71-405a-a1a0-24b14e4de0fb"));
    private static final AccountId REPORTER_ID = new AccountId(
            UUID.fromString("f24a7d0a-7c48-4703-b9d7-a39c933149a1"));
    private static final AccountId RESPONDER_ID = new AccountId(
            UUID.fromString("f2e6c9a8-c1db-4d9d-ad8a-22a342043835"));

    @Test
    void acceptsValidLifecycleStates() {
        assertDoesNotThrow(() -> incident(
                IncidentStatus.DRAFT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of()));
        assertDoesNotThrow(() -> incident(
                IncidentStatus.SUBMITTED,
                Optional.of(SUBMITTED_AT),
                Optional.empty(),
                Optional.empty(),
                List.of(queuedCycle(SUBMITTED_AT))));
        assertDoesNotThrow(() -> incident(
                IncidentStatus.ASSIGNED,
                Optional.of(SUBMITTED_AT),
                Optional.empty(),
                Optional.of(RESPONDER_ID),
                List.of(assignedCycle(SUBMITTED_AT))));
        assertDoesNotThrow(() -> incident(
                IncidentStatus.RESOLVED,
                Optional.of(SUBMITTED_AT),
                Optional.empty(),
                Optional.empty(),
                List.of(resolvedCycle(SUBMITTED_AT, RESOLVED_AT))));
        assertDoesNotThrow(() -> incident(
                IncidentStatus.WITHDRAWN,
                Optional.of(SUBMITTED_AT),
                Optional.of(SUBMITTED_AT),
                Optional.empty(),
                List.of(queuedCycle(SUBMITTED_AT))));
    }

    @Test
    void trimsTitleButPreservesDescriptionAndAnonymousOwnership() {
        Incident incident = new Incident(
                INCIDENT_ID,
                REPORTER_ID,
                "  Network outage  ",
                "  The office network is unavailable.\n",
                IncidentCategory.IT,
                IncidentStatus.SUBMITTED,
                true,
                CREATED_AT,
                Optional.of(SUBMITTED_AT),
                Optional.empty(),
                Optional.empty(),
                List.of(queuedCycle(SUBMITTED_AT)));

        assertEquals("Network outage", incident.title());
        assertEquals("  The office network is unavailable.\n", incident.description());
        assertEquals(REPORTER_ID, incident.reporterId());
    }

    @Test
    void rejectsBlankRequiredContentAndNullCategory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> incidentWithContent(" \n", "Description", IncidentCategory.IT));
        assertThrows(IllegalArgumentException.class, () -> incidentWithContent("Title", " \n", IncidentCategory.IT));
        assertThrows(NullPointerException.class, () -> incidentWithContent("Title", "Description", null));
    }

    @Test
    void draftMayRetainIncompleteText() {
        Incident draft = new Incident(
                INCIDENT_ID,
                REPORTER_ID,
                "  ",
                "",
                IncidentCategory.IT,
                IncidentStatus.DRAFT,
                false,
                CREATED_AT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of());

        assertEquals("", draft.title());
        assertEquals("", draft.description());
    }

    @Test
    void onlyAssignedIncidentMayHaveAssignee() {
        assertThrows(
                IllegalArgumentException.class,
                () -> incident(
                        IncidentStatus.SUBMITTED,
                        Optional.of(SUBMITTED_AT),
                        Optional.empty(),
                        Optional.of(RESPONDER_ID),
                        List.of(queuedCycle(SUBMITTED_AT))));
        assertThrows(
                IllegalArgumentException.class,
                () -> incident(
                        IncidentStatus.ASSIGNED,
                        Optional.of(SUBMITTED_AT),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(assignedCycle(SUBMITTED_AT))));
    }

    @Test
    void assignedIncidentRequiresAssignmentTimestamps() {
        assertThrows(
                IllegalArgumentException.class,
                () -> incident(
                        IncidentStatus.ASSIGNED,
                        Optional.of(SUBMITTED_AT),
                        Optional.empty(),
                        Optional.of(RESPONDER_ID),
                        List.of(queuedCycle(SUBMITTED_AT))));
    }

    @Test
    void statusRequiresMatchingResolutionAndWithdrawalState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> incident(
                        IncidentStatus.RESOLVED,
                        Optional.of(SUBMITTED_AT),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(assignedCycle(SUBMITTED_AT))));
        assertThrows(
                IllegalArgumentException.class,
                () -> incident(
                        IncidentStatus.SUBMITTED,
                        Optional.of(SUBMITTED_AT),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(resolvedCycle(SUBMITTED_AT, RESOLVED_AT))));
        assertThrows(
                IllegalArgumentException.class,
                () -> incident(
                        IncidentStatus.WITHDRAWN,
                        Optional.of(SUBMITTED_AT),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(queuedCycle(SUBMITTED_AT))));
    }

    @Test
    void draftRejectsSubmittedLifecycleData() {
        assertThrows(
                IllegalArgumentException.class,
                () -> incident(
                        IncidentStatus.DRAFT,
                        Optional.of(SUBMITTED_AT),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(queuedCycle(SUBMITTED_AT))));
    }

    @Test
    void rejectsSubmissionBeforeCreationAndMismatchedFirstQueueEntry() {
        assertThrows(
                IllegalArgumentException.class,
                () -> incident(
                        IncidentStatus.SUBMITTED,
                        Optional.of(CREATED_AT.minusSeconds(1)),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(queuedCycle(CREATED_AT.minusSeconds(1)))));
        assertThrows(
                IllegalArgumentException.class,
                () -> incident(
                        IncidentStatus.SUBMITTED,
                        Optional.of(SUBMITTED_AT),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(queuedCycle(SUBMITTED_AT.plusSeconds(1)))));
    }

    @Test
    void rejectsWithdrawalBeforeLatestAssignmentInHandedOffCycle() {
        assertThrows(
                IllegalArgumentException.class,
                () -> incident(
                        IncidentStatus.WITHDRAWN,
                        Optional.of(SUBMITTED_AT),
                        Optional.of(LATEST_ASSIGNED_AT.minusSeconds(1)),
                        Optional.empty(),
                        List.of(assignedCycle(SUBMITTED_AT))));
    }

    @Test
    void retainsMultipleResolutionCyclesAndDerivesReopenCount() {
        ResolutionCycle firstCycle = resolvedCycle(SUBMITTED_AT, RESOLVED_AT);
        ResolutionCycle secondCycle = queuedCycle(REOPENED_AT);
        List<ResolutionCycle> source = new ArrayList<>(List.of(firstCycle, secondCycle));

        Incident incident = incident(
                IncidentStatus.SUBMITTED,
                Optional.of(SUBMITTED_AT),
                Optional.empty(),
                Optional.empty(),
                source);
        source.clear();

        assertEquals(2, incident.resolutionCycles().size());
        assertEquals(1, incident.reopenCount());
        assertEquals(secondCycle, incident.currentCycle().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> incident.resolutionCycles().clear());
    }

    @Test
    void rejectsNewCycleBeforePriorResolutionOrAfterIncompleteCycle() {
        assertThrows(
                IllegalArgumentException.class,
                () -> incident(
                        IncidentStatus.SUBMITTED,
                        Optional.of(SUBMITTED_AT),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(
                                resolvedCycle(SUBMITTED_AT, RESOLVED_AT),
                                queuedCycle(RESOLVED_AT.minusSeconds(1)))));
        assertThrows(
                IllegalArgumentException.class,
                () -> incident(
                        IncidentStatus.SUBMITTED,
                        Optional.of(SUBMITTED_AT),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(queuedCycle(SUBMITTED_AT), queuedCycle(REOPENED_AT))));
    }

    private Incident incident(
            IncidentStatus status,
            Optional<Instant> submittedAt,
            Optional<Instant> withdrawnAt,
            Optional<AccountId> assigneeId,
            List<ResolutionCycle> resolutionCycles) {
        return new Incident(
                INCIDENT_ID,
                REPORTER_ID,
                "Incident title",
                "Incident description",
                IncidentCategory.IT,
                status,
                false,
                CREATED_AT,
                submittedAt,
                withdrawnAt,
                assigneeId,
                resolutionCycles);
    }

    private Incident incidentWithContent(String title, String description, IncidentCategory category) {
        return new Incident(
                INCIDENT_ID,
                REPORTER_ID,
                title,
                description,
                category,
                IncidentStatus.SUBMITTED,
                false,
                CREATED_AT,
                Optional.of(SUBMITTED_AT),
                Optional.empty(),
                Optional.empty(),
                List.of(queuedCycle(SUBMITTED_AT)));
    }

    private ResolutionCycle queuedCycle(Instant queueEnteredAt) {
        return new ResolutionCycle(
                queueEnteredAt,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private ResolutionCycle assignedCycle(Instant queueEnteredAt) {
        return new ResolutionCycle(
                queueEnteredAt,
                Optional.of(FIRST_ASSIGNED_AT),
                Optional.of(LATEST_ASSIGNED_AT),
                Optional.empty());
    }

    private ResolutionCycle resolvedCycle(Instant queueEnteredAt, Instant resolvedAt) {
        Resolution resolution = new Resolution(
                "Resolved",
                resolvedAt,
                RESPONDER_ID,
                RESPONDER_ID);
        return new ResolutionCycle(
                queueEnteredAt,
                Optional.of(FIRST_ASSIGNED_AT),
                Optional.of(LATEST_ASSIGNED_AT),
                Optional.of(resolution));
    }
}

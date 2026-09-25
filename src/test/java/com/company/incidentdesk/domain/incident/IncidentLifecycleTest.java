package com.company.incidentdesk.domain.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;

/** Tests every canonical lifecycle operation and unsupported source state. */
class IncidentLifecycleTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-19T08:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-19T08:01:00Z");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-19T08:02:00Z");
    private static final Instant REASSIGNED_AT = Instant.parse("2026-09-19T08:03:00Z");
    private static final Instant RESOLVED_AT = Instant.parse("2026-09-19T08:04:00Z");
    private static final Instant REOPENED_AT = Instant.parse("2026-09-19T08:05:00Z");
    private static final IncidentId INCIDENT_ID = new IncidentId(
            UUID.fromString("61e27e29-7a71-405a-a1a0-24b14e4de0fb"));
    private static final AccountId REPORTER_ID = new AccountId(
            UUID.fromString("f24a7d0a-7c48-4703-b9d7-a39c933149a1"));
    private static final AccountId RESPONDER_ID = new AccountId(
            UUID.fromString("f2e6c9a8-c1db-4d9d-ad8a-22a342043835"));
    private static final AccountId OTHER_RESPONDER_ID = new AccountId(
            UUID.fromString("2bf27a40-726d-423c-ad19-bc781426b2be"));
    private static final AccountId ADMIN_ID = new AccountId(
            UUID.fromString("5181fa2d-1644-44e0-8202-53283902ee27"));

    @Test
    void createsDraftAndDirectSubmissionFromInjectedClock() {
        Incident draft = lifecycleAt(CREATED_AT).saveDraft(
                INCIDENT_ID,
                REPORTER_ID,
                "  ",
                "",
                IncidentCategory.IT,
                true);

        assertEquals(IncidentStatus.DRAFT, draft.status());
        assertEquals(CREATED_AT, draft.createdAt());
        assertTrue(draft.submittedAt().isEmpty());
        assertTrue(draft.resolutionCycles().isEmpty());

        Incident submitted = lifecycleAt(SUBMITTED_AT).submit(
                INCIDENT_ID,
                REPORTER_ID,
                "  Printer failure  ",
                "  The printer is jammed.\n",
                IncidentCategory.FACILITIES,
                false);

        assertEquals(IncidentStatus.SUBMITTED, submitted.status());
        assertEquals("Printer failure", submitted.title());
        assertEquals("  The printer is jammed.\n", submitted.description());
        assertEquals(SUBMITTED_AT, submitted.createdAt());
        assertEquals(Optional.of(SUBMITTED_AT), submitted.submittedAt());
        assertEquals(SUBMITTED_AT, submitted.currentCycle().orElseThrow().queueEnteredAt());
    }

    @Test
    void submitsDraftAndPreservesCreationDetails() {
        Incident draft = draft();

        Incident submitted = lifecycleAt(SUBMITTED_AT).submit(draft);

        assertEquals(IncidentStatus.SUBMITTED, submitted.status());
        assertEquals(draft.id(), submitted.id());
        assertEquals(draft.reporterId(), submitted.reporterId());
        assertEquals(CREATED_AT, submitted.createdAt());
        assertEquals(Optional.of(SUBMITTED_AT), submitted.submittedAt());
        assertEquals(SUBMITTED_AT, submitted.currentCycle().orElseThrow().queueEnteredAt());
        assertEquals(IncidentStatus.DRAFT, draft.status());
    }

    @Test
    void editsDraftAndSubmittedIncidentWithoutChangingLifecycleTimes() {
        Incident editedDraft = lifecycleAt(REOPENED_AT).edit(
                draft(),
                "  Updated draft  ",
                "  Updated draft text.\n",
                IncidentCategory.HUMAN_RELATIONS,
                true);

        assertEquals("Updated draft", editedDraft.title());
        assertEquals("  Updated draft text.\n", editedDraft.description());
        assertEquals(IncidentCategory.HUMAN_RELATIONS, editedDraft.category());
        assertTrue(editedDraft.anonymous());
        assertEquals(CREATED_AT, editedDraft.createdAt());

        Incident submitted = submitted();
        Incident editedSubmitted = lifecycleAt(REOPENED_AT).edit(
                submitted,
                "Updated report",
                "Updated submitted text",
                IncidentCategory.FACILITIES,
                false);

        assertEquals(IncidentStatus.SUBMITTED, editedSubmitted.status());
        assertEquals(submitted.submittedAt(), editedSubmitted.submittedAt());
        assertEquals(submitted.resolutionCycles(), editedSubmitted.resolutionCycles());
    }

    @Test
    void withdrawsSubmittedIncidentAtClockTime() {
        Incident submitted = submitted();

        Incident withdrawn = lifecycleAt(ASSIGNED_AT).withdraw(submitted);

        assertEquals(IncidentStatus.WITHDRAWN, withdrawn.status());
        assertEquals(Optional.of(ASSIGNED_AT), withdrawn.withdrawnAt());
        assertEquals(submitted.submittedAt(), withdrawn.submittedAt());
        assertEquals(submitted.resolutionCycles(), withdrawn.resolutionCycles());
        assertEquals(IncidentStatus.SUBMITTED, submitted.status());
    }

    @Test
    void claimAndAssignmentSetAssigneeAndAssignmentTimes() {
        Incident claimed = lifecycleAt(ASSIGNED_AT).claim(submitted(), RESPONDER_ID);

        assertAssignment(claimed, RESPONDER_ID, ASSIGNED_AT, ASSIGNED_AT);

        Incident assigned = lifecycleAt(ASSIGNED_AT).assign(submitted(), OTHER_RESPONDER_ID);

        assertAssignment(assigned, OTHER_RESPONDER_ID, ASSIGNED_AT, ASSIGNED_AT);
    }

    @Test
    void claimAfterHandoffPreservesFirstAssignmentAndQueuePosition() {
        Incident handedOff = lifecycleAt(REASSIGNED_AT).handoff(assigned());

        Incident claimedAgain = lifecycleAt(REOPENED_AT).claim(handedOff, OTHER_RESPONDER_ID);

        assertAssignment(claimedAgain, OTHER_RESPONDER_ID, ASSIGNED_AT, REOPENED_AT);
        assertEquals(SUBMITTED_AT, claimedAgain.currentCycle().orElseThrow().queueEnteredAt());
    }

    @Test
    void reassignsWithinCurrentCycleAndPreservesFirstAssignment() {
        Incident assigned = assigned();

        Incident reassigned = lifecycleAt(REASSIGNED_AT).reassign(assigned, OTHER_RESPONDER_ID);

        assertAssignment(reassigned, OTHER_RESPONDER_ID, ASSIGNED_AT, REASSIGNED_AT);
        assertEquals(SUBMITTED_AT, reassigned.currentCycle().orElseThrow().queueEnteredAt());
        assertEquals(Optional.of(RESPONDER_ID), assigned.assigneeId());
    }

    @Test
    void reassignsUnassignedSubmittedIncidentSettingFirstAssignment() {
        Incident submitted = submitted();

        Incident reassigned = lifecycleAt(ASSIGNED_AT).reassign(submitted, OTHER_RESPONDER_ID);

        assertAssignment(reassigned, OTHER_RESPONDER_ID, ASSIGNED_AT, ASSIGNED_AT);
        assertEquals(SUBMITTED_AT, reassigned.currentCycle().orElseThrow().queueEnteredAt());
        assertTrue(submitted.assigneeId().isEmpty());
    }

    @Test
    void resolvesAssignedIncidentWithPreservedRemarksAndAttribution() {
        String remarks = "  Replaced the damaged cable.\n";

        Incident resolved = lifecycleAt(RESOLVED_AT).resolve(assigned(), ADMIN_ID, remarks);

        assertEquals(IncidentStatus.RESOLVED, resolved.status());
        assertTrue(resolved.assigneeId().isEmpty());
        Resolution resolution = resolved.currentCycle().orElseThrow().resolution().orElseThrow();
        assertEquals(remarks, resolution.remarks());
        assertEquals(RESOLVED_AT, resolution.resolvedAt());
        assertEquals(ADMIN_ID, resolution.resolvedBy());
        assertEquals(RESPONDER_ID, resolution.responderAtResolution());
    }

    @Test
    void handoffClearsAssigneeAndRetainsOriginalQueuePosition() {
        Incident assigned = assigned();

        Incident handedOff = lifecycleAt(REASSIGNED_AT).handoff(assigned);

        assertEquals(IncidentStatus.SUBMITTED, handedOff.status());
        assertTrue(handedOff.assigneeId().isEmpty());
        assertEquals(assigned.submittedAt(), handedOff.submittedAt());
        assertEquals(assigned.resolutionCycles(), handedOff.resolutionCycles());
        assertEquals(SUBMITTED_AT, handedOff.currentCycle().orElseThrow().queueEnteredAt());
    }

    @Test
    void reopenReturnsIncidentAndExplanationForOneLogicalCommit() {
        Incident resolved = resolved();
        String explanationText = "  The outage returned after five minutes.\n";

        ReopenTransition transition = lifecycleAt(REOPENED_AT).reopen(
                resolved,
                REPORTER_ID,
                explanationText);

        Incident reopened = transition.incident();
        assertEquals(IncidentStatus.SUBMITTED, reopened.status());
        assertTrue(reopened.assigneeId().isEmpty());
        assertEquals(2, reopened.resolutionCycles().size());
        assertEquals(1, reopened.reopenCount());
        assertEquals(REOPENED_AT, reopened.currentCycle().orElseThrow().queueEnteredAt());
        assertTrue(reopened.currentCycle().orElseThrow().resolution().isEmpty());
        assertEquals(resolved.resolutionCycles().getFirst(), reopened.resolutionCycles().getFirst());

        ReopenExplanation explanation = transition.explanation();
        assertEquals(REPORTER_ID, explanation.authorId());
        assertEquals(explanationText, explanation.text());
        assertEquals(REOPENED_AT, explanation.createdAt());
        assertEquals(2, explanation.resolutionCycleNumber());
    }

    @Test
    void rejectsBlankResolutionAndReopenTextWithoutChangingOriginal() {
        Incident assigned = assigned();
        Incident resolved = resolved();

        assertThrows(
                IllegalArgumentException.class,
                () -> lifecycleAt(RESOLVED_AT).resolve(assigned, RESPONDER_ID, " \n"));
        assertThrows(
                IllegalArgumentException.class,
                () -> lifecycleAt(REOPENED_AT).reopen(resolved, REPORTER_ID, " \n"));

        assertEquals(IncidentStatus.ASSIGNED, assigned.status());
        assertFalse(assigned.currentCycle().orElseThrow().isResolved());
        assertEquals(IncidentStatus.RESOLVED, resolved.status());
        assertEquals(1, resolved.resolutionCycles().size());
    }

    @Test
    void rejectsEveryUnsupportedSourceStateWithoutChangingOriginal() {
        Map<IncidentStatus, Incident> incidents = incidentsByStatus();
        Map<IncidentAction, Set<IncidentStatus>> allowedStatuses = allowedStatuses();

        for (Map.Entry<IncidentAction, Set<IncidentStatus>> entry : allowedStatuses.entrySet()) {
            for (Map.Entry<IncidentStatus, Incident> incidentEntry : incidents.entrySet()) {
                if (entry.getValue().contains(incidentEntry.getKey())) {
                    continue;
                }
                Incident original = incidentEntry.getValue();
                InvalidIncidentTransitionException failure = assertThrows(
                        InvalidIncidentTransitionException.class,
                        () -> invoke(entry.getKey(), original),
                        entry.getKey() + " from " + incidentEntry.getKey());
                assertEquals(entry.getKey(), failure.action());
                assertEquals(incidentEntry.getKey(), failure.actualStatus());
                assertEquals(entry.getValue(), failure.expectedStatuses());
                assertEquals(original, incidentEntry.getValue());
            }
        }
    }

    @Test
    void returnedSnapshotsDoNotShareMutableResolutionLists() {
        Incident original = submitted();

        Incident claimed = lifecycleAt(ASSIGNED_AT).claim(original, RESPONDER_ID);

        assertNotSame(original.resolutionCycles(), claimed.resolutionCycles());
        assertThrows(UnsupportedOperationException.class, () -> claimed.resolutionCycles().clear());
        assertEquals(IncidentStatus.SUBMITTED, original.status());
    }

    private void invoke(IncidentAction action, Incident incident) {
        IncidentLifecycle lifecycle = lifecycleAt(REOPENED_AT);
        switch (action) {
        case SUBMIT -> lifecycle.submit(incident);
        case EDIT -> lifecycle.edit(
                incident,
                "Edited title",
                "Edited description",
                IncidentCategory.IT,
                false);
        case WITHDRAW -> lifecycle.withdraw(incident);
        case CLAIM -> lifecycle.claim(incident, RESPONDER_ID);
        case ASSIGN -> lifecycle.assign(incident, RESPONDER_ID);
        case REASSIGN -> lifecycle.reassign(incident, OTHER_RESPONDER_ID);
        case RESOLVE -> lifecycle.resolve(incident, RESPONDER_ID, "Resolved");
        case HANDOFF -> lifecycle.handoff(incident);
        case REOPEN -> lifecycle.reopen(incident, REPORTER_ID, "Still unresolved");
        case SAVE_DRAFT -> throw new IllegalArgumentException("save draft has no source incident");
        }
    }

    private static Map<IncidentAction, Set<IncidentStatus>> allowedStatuses() {
        Map<IncidentAction, Set<IncidentStatus>> statuses = new EnumMap<>(IncidentAction.class);
        statuses.put(IncidentAction.SUBMIT, Set.of(IncidentStatus.DRAFT));
        statuses.put(IncidentAction.EDIT, Set.of(IncidentStatus.DRAFT, IncidentStatus.SUBMITTED));
        statuses.put(IncidentAction.WITHDRAW, Set.of(IncidentStatus.SUBMITTED));
        statuses.put(IncidentAction.CLAIM, Set.of(IncidentStatus.SUBMITTED));
        statuses.put(IncidentAction.ASSIGN, Set.of(IncidentStatus.SUBMITTED));
        statuses.put(IncidentAction.REASSIGN, Set.of(IncidentStatus.SUBMITTED, IncidentStatus.ASSIGNED));
        statuses.put(IncidentAction.RESOLVE, Set.of(IncidentStatus.ASSIGNED));
        statuses.put(IncidentAction.HANDOFF, Set.of(IncidentStatus.ASSIGNED));
        statuses.put(IncidentAction.REOPEN, Set.of(IncidentStatus.RESOLVED));
        return statuses;
    }

    private Map<IncidentStatus, Incident> incidentsByStatus() {
        Map<IncidentStatus, Incident> incidents = new EnumMap<>(IncidentStatus.class);
        incidents.put(IncidentStatus.DRAFT, draft());
        incidents.put(IncidentStatus.SUBMITTED, submitted());
        incidents.put(IncidentStatus.ASSIGNED, assigned());
        incidents.put(IncidentStatus.RESOLVED, resolved());
        incidents.put(IncidentStatus.WITHDRAWN, withdrawn());
        return incidents;
    }

    private Incident draft() {
        return lifecycleAt(CREATED_AT).saveDraft(
                INCIDENT_ID,
                REPORTER_ID,
                "Incident title",
                "Incident description",
                IncidentCategory.IT,
                false);
    }

    private Incident submitted() {
        return lifecycleAt(SUBMITTED_AT).submit(draft());
    }

    private Incident assigned() {
        return lifecycleAt(ASSIGNED_AT).claim(submitted(), RESPONDER_ID);
    }

    private Incident resolved() {
        return lifecycleAt(RESOLVED_AT).resolve(assigned(), RESPONDER_ID, "Resolved");
    }

    private Incident withdrawn() {
        return lifecycleAt(ASSIGNED_AT).withdraw(submitted());
    }

    private static IncidentLifecycle lifecycleAt(Instant instant) {
        return new IncidentLifecycle(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static void assertAssignment(
            Incident incident,
            AccountId expectedAssignee,
            Instant expectedFirstAssignment,
            Instant expectedLatestAssignment) {
        assertEquals(IncidentStatus.ASSIGNED, incident.status());
        assertEquals(Optional.of(expectedAssignee), incident.assigneeId());
        ResolutionCycle cycle = incident.currentCycle().orElseThrow();
        assertEquals(Optional.of(expectedFirstAssignment), cycle.firstAssignedAt());
        assertEquals(Optional.of(expectedLatestAssignment), cycle.latestAssignedAt());
        assertTrue(cycle.resolution().isEmpty());
    }
}

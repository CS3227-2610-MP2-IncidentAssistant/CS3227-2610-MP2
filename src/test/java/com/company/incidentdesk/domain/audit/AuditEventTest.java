package com.company.incidentdesk.domain.audit;

import static com.company.incidentdesk.testutil.AuditTestData.ACTOR_ID;
import static com.company.incidentdesk.testutil.AuditTestData.INCIDENT_TARGET;
import static com.company.incidentdesk.testutil.AuditTestData.OCCURRED_AT;
import static com.company.incidentdesk.testutil.AuditTestData.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.Role;

/** Tests audit event structure, privacy boundaries, and required action coverage. */
class AuditEventTest {
    @Test
    void copiesStructuredChangesAndKeepsThemImmutable() {
        List<AuditChange> source = new ArrayList<>();
        source.add(AuditChange.changed(
                AuditChangeField.INCIDENT_STATUS,
                "SUBMITTED",
                "ASSIGNED"));

        AuditEvent auditEvent = new AuditEvent(
                eventId("00000000-0000-0000-0000-000000000001"),
                OCCURRED_AT,
                new AuditActor(ACTOR_ID, Role.REPORTER, AuditActorVisibility.STANDARD),
                AuditAction.INCIDENT_CLAIMED,
                INCIDENT_TARGET,
                AuditOutcome.SUCCESS,
                source,
                Optional.empty());
        source.clear();

        assertEquals(1, auditEvent.changes().size());
        assertThrows(UnsupportedOperationException.class, () -> auditEvent.changes().clear());
    }

    @Test
    void changeSummaryRejectsEmptyOrBlankValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditChange(
                        AuditChangeField.CATEGORY,
                        Optional.empty(),
                        Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> AuditChange.added(AuditChangeField.FAILURE_CODE, " \n"));
    }

    @Test
    void anonymousVisibilityCanOnlyBeAppliedToReporterActors() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditActor(ACTOR_ID, Role.ADMINISTRATOR, AuditActorVisibility.ANONYMOUS_REPORTER));
    }

    @Test
    void equalTimestampsUseEventIdentifierAsTieBreaker() {
        AuditEvent second = event("00000000-0000-0000-0000-000000000002", OCCURRED_AT);
        AuditEvent first = event("00000000-0000-0000-0000-000000000001", OCCURRED_AT);

        List<AuditEvent> sorted = new ArrayList<>(List.of(second, first));
        sorted.sort(AuditEvent.CHRONOLOGICAL_ORDER);

        assertEquals(List.of(first, second), sorted);
    }

    @Test
    void actionTypeExplicitlyCoversEveryRequiredAuditedOperation() {
        Set<AuditAction> expected = EnumSet.of(
                AuditAction.DRAFT_CREATED,
                AuditAction.INCIDENT_CREATED,
                AuditAction.INCIDENT_SUBMITTED,
                AuditAction.INCIDENT_EDITED,
                AuditAction.INCIDENT_WITHDRAWN,
                AuditAction.INCIDENT_CLAIMED,
                AuditAction.INCIDENT_ASSIGNED,
                AuditAction.INCIDENT_REASSIGNED,
                AuditAction.INCIDENT_RESOLVED,
                AuditAction.INCIDENT_HANDED_OFF,
                AuditAction.INCIDENT_REOPENED,
                AuditAction.COMMENT_ADDED,
                AuditAction.ATTACHMENT_ADDED,
                AuditAction.ATTACHMENT_REMOVED,
                AuditAction.ACCOUNT_REGISTERED,
                AuditAction.AUTHENTICATION_SUCCEEDED,
                AuditAction.AUTHENTICATION_FAILED,
                AuditAction.RESPONDER_PROMOTION_REQUESTED,
                AuditAction.RESPONDER_PROMOTION_APPROVED,
                AuditAction.RESPONDER_PROMOTION_REJECTED,
                AuditAction.RESPONDER_ACCESS_CHANGED,
                AuditAction.ACCOUNT_DISABLED,
                AuditAction.ACCOUNT_DELETED,
                AuditAction.PASSWORD_CHANGED,
                AuditAction.PASSWORD_RESET_INITIATED,
                AuditAction.PASSWORD_RESET_COMPLETED,
                AuditAction.SLO_CONFIGURATION_CHANGED,
                AuditAction.DATA_MIGRATED,
                AuditAction.BACKUP_RESTORED,
                AuditAction.STORAGE_CORRUPTION_DETECTED);

        assertEquals(expected, EnumSet.allOf(AuditAction.class));
    }

    @Test
    void eventStructureHasNoFieldForPasswordsSessionsOrActorDisplayNames() {
        Set<String> componentNames = java.util.Arrays.stream(AuditEvent.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(java.util.stream.Collectors.toSet());

        assertFalse(componentNames.stream().anyMatch(name -> name.contains("password")));
        assertFalse(componentNames.stream().anyMatch(name -> name.contains("session")));
        assertFalse(componentNames.stream().anyMatch(name -> name.contains("login")));
        assertFalse(componentNames.stream().anyMatch(name -> name.contains("remark")));
        assertFalse(componentNames.stream().anyMatch(name -> name.contains("commenttext")));
    }

    private static AuditEventId eventId(String value) {
        return new AuditEventId(UUID.fromString(value));
    }
}

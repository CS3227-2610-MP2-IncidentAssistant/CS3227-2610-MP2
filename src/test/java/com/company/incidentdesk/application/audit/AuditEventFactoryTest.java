package com.company.incidentdesk.application.audit;

import static com.company.incidentdesk.testutil.AuditTestData.INCIDENT_TARGET;
import static com.company.incidentdesk.testutil.AuditTestData.OCCURRED_AT;
import static com.company.incidentdesk.testutil.AuditTestData.reporter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditChange;
import com.company.incidentdesk.domain.audit.AuditChangeField;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.audit.AuditEvidenceReference;
import com.company.incidentdesk.domain.audit.AuditEvidenceType;
import com.company.incidentdesk.domain.audit.AuditOutcome;

/** Tests deterministic, privacy-safe audit event creation. */
class AuditEventFactoryTest {
    private static final AuditEventId EVENT_ID = new AuditEventId(
            UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void factoryGeneratesIdentifierTimestampAndRoleSnapshot() {
        Account actor = reporter("SensitiveLoginName");
        AuditEventFactory factory = new AuditEventFactory(
                Clock.fixed(OCCURRED_AT, ZoneOffset.UTC),
                () -> EVENT_ID);

        AuditEvent event = factory.create(
                actor,
                AuditActorVisibility.ANONYMOUS_REPORTER,
                AuditAction.INCIDENT_RESOLVED,
                INCIDENT_TARGET,
                AuditOutcome.SUCCESS,
                List.of(AuditChange.changed(
                        AuditChangeField.INCIDENT_STATUS,
                        "ASSIGNED",
                        "RESOLVED")),
                Optional.of(new AuditEvidenceReference(
                        AuditEvidenceType.RESOLUTION,
                        "resolution-cycle-1")));

        assertEquals(EVENT_ID, event.id());
        assertEquals(OCCURRED_AT, event.occurredAt());
        assertEquals(actor.id(), event.actor().accountId());
        assertEquals(actor.role(), event.actor().role());
        assertEquals(AuditActorVisibility.ANONYMOUS_REPORTER, event.actor().visibility());
        assertFalse(event.toString().contains(actor.loginName()));
    }
}

package com.company.incidentdesk.testutil;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActor;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;

/** Deterministic audit fixtures shared across domain, application, and persistence tests. */
public final class AuditTestData {
    public static final Instant OCCURRED_AT = Instant.parse("2026-09-19T08:00:00Z");
    public static final AccountId ACTOR_ID = new AccountId(
            UUID.fromString("f24a7d0a-7c48-4703-b9d7-a39c933149a1"));
    public static final AuditTarget INCIDENT_TARGET = new AuditTarget(
            AuditTargetType.INCIDENT,
            "61e27e29-7a71-405a-a1a0-24b14e4de0fb");

    private AuditTestData() {
    }

    public static Account reporter(String loginName) {
        return new Account(
                ACTOR_ID,
                loginName,
                Role.REPORTER,
                AccountStatus.ENABLED,
                ResponderAccess.NONE);
    }

    public static AuditEvent event(String identifier, Instant occurredAt) {
        return event(
                identifier,
                occurredAt,
                AuditActorVisibility.STANDARD,
                AuditAction.INCIDENT_SUBMITTED,
                INCIDENT_TARGET);
    }

    public static AuditEvent event(
            String identifier,
            Instant occurredAt,
            AuditActorVisibility visibility,
            AuditAction action,
            AuditTarget target) {
        return new AuditEvent(
                new AuditEventId(UUID.fromString(identifier)),
                occurredAt,
                new AuditActor(ACTOR_ID, Role.REPORTER, visibility),
                action,
                target,
                AuditOutcome.SUCCESS,
                List.of(),
                Optional.empty());
    }
}

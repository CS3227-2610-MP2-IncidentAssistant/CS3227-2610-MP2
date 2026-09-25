package com.company.incidentdesk.application.audit;

import java.time.Instant;
import java.util.Objects;

import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTargetType;

/** Privacy-safe presentation data for one application audit event. */
public record AuditLogEntry(
        String eventIdentifier,
        Instant occurredAt,
        String actorLabel,
        Role actorRole,
        AuditAction action,
        AuditTargetType targetType,
        String targetIdentifier,
        AuditOutcome outcome,
        String eventDescription,
        String details) {
    public AuditLogEntry {
        Objects.requireNonNull(eventIdentifier, "eventIdentifier");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actorLabel, "actorLabel");
        Objects.requireNonNull(actorRole, "actorRole");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(eventDescription, "eventDescription");
        Objects.requireNonNull(details, "details");
    }
}

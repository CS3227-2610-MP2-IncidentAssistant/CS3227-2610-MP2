package com.company.incidentdesk.application.audit;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActor;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditChange;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.audit.AuditEvidenceReference;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;

/** Creates audit events with application-generated identifiers and UTC instants. */
public final class AuditEventFactory {
    private final Clock clock;
    private final Supplier<AuditEventId> identifierGenerator;

    public AuditEventFactory(Clock clock, Supplier<AuditEventId> identifierGenerator) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identifierGenerator = Objects.requireNonNull(identifierGenerator, "identifierGenerator");
    }

    public AuditEvent create(
            Account actor,
            AuditActorVisibility visibility,
            AuditAction action,
            AuditTarget target,
            AuditOutcome outcome,
            List<AuditChange> changes,
            Optional<AuditEvidenceReference> evidenceReference) {
        Account requiredActor = Objects.requireNonNull(actor, "actor");
        AuditActor auditActor = new AuditActor(requiredActor.id(), requiredActor.role(), visibility);
        return new AuditEvent(
                Objects.requireNonNull(identifierGenerator.get(), "generated audit event identifier"),
                clock.instant(),
                auditActor,
                action,
                target,
                outcome,
                changes,
                evidenceReference);
    }
}

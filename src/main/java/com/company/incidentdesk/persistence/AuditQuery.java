package com.company.incidentdesk.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditTarget;

/** Optional time, actor, action, and target filters for the application audit log. */
public record AuditQuery(
        Optional<Instant> fromInclusive,
        Optional<Instant> toExclusive,
        Optional<AccountId> actorId,
        Set<AuditAction> actions,
        Optional<AuditTarget> target) {
    public AuditQuery {
        Objects.requireNonNull(fromInclusive, "fromInclusive");
        Objects.requireNonNull(toExclusive, "toExclusive");
        Objects.requireNonNull(actorId, "actorId");
        actions = Set.copyOf(Objects.requireNonNull(actions, "actions"));
        Objects.requireNonNull(target, "target");
        if (fromInclusive.isPresent()
                && toExclusive.isPresent()
                && !fromInclusive.orElseThrow().isBefore(toExclusive.orElseThrow())) {
            throw new IllegalArgumentException("audit query start must be before its exclusive end");
        }
    }

    public static AuditQuery all() {
        return new AuditQuery(Optional.empty(), Optional.empty(), Optional.empty(), Set.of(), Optional.empty());
    }

    public static AuditQuery forTarget(AuditTarget target) {
        return new AuditQuery(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                Optional.of(Objects.requireNonNull(target, "target")));
    }
}

package com.company.incidentdesk.domain.audit;

import java.util.Objects;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.Role;

/** Stable actor identity and role snapshot retained independently of account deletion. */
public record AuditActor(AccountId accountId, Role role, AuditActorVisibility visibility) {
    public AuditActor {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(visibility, "visibility");
        if (visibility == AuditActorVisibility.ANONYMOUS_REPORTER && role != Role.REPORTER) {
            throw new IllegalArgumentException("anonymous audit visibility is limited to reporters");
        }
    }
}

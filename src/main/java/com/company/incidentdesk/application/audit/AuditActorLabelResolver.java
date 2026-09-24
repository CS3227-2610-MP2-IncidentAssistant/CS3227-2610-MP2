package com.company.incidentdesk.application.audit;

import java.util.Objects;

import com.company.incidentdesk.application.account.AccountLookup;
import com.company.incidentdesk.domain.audit.AuditActor;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;

/** Resolves privacy-safe labels without storing account display names in audit records. */
public final class AuditActorLabelResolver {
    public static final String ANONYMOUS_REPORTER_LABEL = "Anonymous reporter";
    public static final String DELETED_ACCOUNT_LABEL = "Deleted account";

    private final AccountLookup accountLookup;

    public AuditActorLabelResolver(AccountLookup accountLookup) {
        this.accountLookup = Objects.requireNonNull(accountLookup, "accountLookup");
    }

    public String resolve(AuditActor actor) {
        AuditActor requiredActor = Objects.requireNonNull(actor, "actor");
        if (requiredActor.visibility() == AuditActorVisibility.ANONYMOUS_REPORTER) {
            return ANONYMOUS_REPORTER_LABEL;
        }
        return accountLookup.findById(requiredActor.accountId())
                .map(account -> account.isDeleted() ? DELETED_ACCOUNT_LABEL : account.loginName())
                .orElse(DELETED_ACCOUNT_LABEL);
    }
}

package com.company.incidentdesk.application.session;

import java.time.Instant;
import java.util.Objects;

import com.company.incidentdesk.domain.account.AccountId;

/** Identifies the single account authenticated in the running application. */
public record AuthenticatedSession(AccountId accountId, Instant authenticatedAt) {
    /**
     * Creates an authenticated session.
     *
     * @param accountId authenticated account identifier
     * @param authenticatedAt application-generated UTC authentication time
     */
    public AuthenticatedSession {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(authenticatedAt, "authenticatedAt");
    }
}

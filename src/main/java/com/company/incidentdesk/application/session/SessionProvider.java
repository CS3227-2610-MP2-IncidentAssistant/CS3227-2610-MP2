package com.company.incidentdesk.application.session;

import java.util.Optional;

import com.company.incidentdesk.domain.account.Account;

/** Read-only access to the active authenticated application session. */
public interface SessionProvider {
    /**
     * Returns the active session when its account still exists and is enabled.
     *
     * @return current valid session, or empty when signed out
     */
    Optional<AuthenticatedSession> currentSession();

    /**
     * Reloads and returns the current account and its latest permissions.
     *
     * @return current enabled account, or empty when signed out
     */
    Optional<Account> currentAccount();
}

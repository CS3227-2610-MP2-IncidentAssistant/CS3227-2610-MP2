package com.company.incidentdesk.application.session;

import com.company.incidentdesk.domain.account.AccountId;

/** Authentication operations for the application's single active session. */
public interface SessionService extends SessionProvider {
    /**
     * Authenticates using an exact, case-sensitive login name.
     *
     * <p>A successful login replaces any active session. A rejected login
     * leaves the active session unchanged. The caller remains responsible for
     * clearing its password array after this call.</p>
     *
     * @param loginName exact login name
     * @param password candidate password
     * @return privacy-safe authentication outcome
     */
    AuthenticationResult login(String loginName, char[] password);

    /** Clears the active session. */
    void logout();

    /** Invalidates the active session when it belongs to the specified account. */
    default void invalidate(AccountId accountId) {
        currentSession().filter(session -> session.accountId().equals(accountId)).ifPresent(ignored -> logout());
    }

    /** Returns whether the active account must replace a temporary credential. */
    default boolean requiresPasswordChange() { return false; }
}

package com.company.incidentdesk.application.session;

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
}

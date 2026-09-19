package com.company.incidentdesk.application.account;

import com.company.incidentdesk.domain.account.AccountId;

/** Verifies credentials without exposing password storage to session logic. */
@FunctionalInterface
public interface PasswordVerifier {
    /**
     * Verifies a candidate password for an account.
     *
     * <p>The supplied array is temporary and is cleared immediately after this
     * method returns. Implementations must not retain it.</p>
     *
     * @param accountId account whose credential should be checked
     * @param candidatePassword temporary candidate password
     * @return true when the password matches
     */
    boolean verify(AccountId accountId, char[] candidatePassword);
}

package com.company.incidentdesk.application.account;

/** Privacy-safe outcomes for an authenticated account password change. */
public enum PasswordChangeResult {
    CHANGED,
    INVALID_CURRENT_PASSWORD,
    INVALID_NEW_PASSWORD,
    NEW_PASSWORD_MISMATCH,
    FAILED
}

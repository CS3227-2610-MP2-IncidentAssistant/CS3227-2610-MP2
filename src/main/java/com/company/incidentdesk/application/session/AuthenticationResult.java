package com.company.incidentdesk.application.session;

/** Privacy-safe outcome of an authentication attempt. */
public enum AuthenticationResult {
    AUTHENTICATED,
    PASSWORD_CHANGE_REQUIRED,
    REJECTED
}

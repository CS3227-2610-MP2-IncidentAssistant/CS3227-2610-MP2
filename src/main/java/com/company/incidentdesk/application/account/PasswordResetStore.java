package com.company.incidentdesk.application.account;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.audit.AuditEvent;

/** Atomically stores an administrator-issued temporary credential and its audit event. */
public interface PasswordResetStore extends AccountLookup {
    void resetPassword(AccountId accountId, PasswordCredential credential, AuditEvent auditEvent);
}

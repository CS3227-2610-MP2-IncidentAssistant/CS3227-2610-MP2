package com.company.incidentdesk.application.account;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.audit.AuditEvent;

/** Atomic credential replacement and audit persistence boundary. */
public interface PasswordChangeStore {
    void changePassword(AccountId accountId, PasswordCredential credential, AuditEvent auditEvent);
}

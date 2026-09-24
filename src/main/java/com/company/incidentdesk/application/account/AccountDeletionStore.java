package com.company.incidentdesk.application.account;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.audit.AuditEvent;

/** Atomic persistence boundary for account tombstoning and its audit evidence. */
public interface AccountDeletionStore extends AccountLookup {
    void delete(Account tombstone, AuditEvent auditEvent);
}

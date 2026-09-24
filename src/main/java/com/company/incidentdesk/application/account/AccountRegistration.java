package com.company.incidentdesk.application.account;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.audit.AuditEvent;

/** Account, credential, and audit event committed as one logical operation. */
public record AccountRegistration(Account account, PasswordCredential credential, AuditEvent auditEvent) { }

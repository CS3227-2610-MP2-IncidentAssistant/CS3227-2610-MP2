package com.company.incidentdesk.persistence.file;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.comment.IncidentComment;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.domain.slo.SloTargetVersionId;
import com.company.incidentdesk.application.account.PasswordCredential;

/** Immutable aggregate committed as one canonical application-data file. */
record LocalApplicationState(
        Map<AccountId, Account> accounts,
        Map<AccountId, PasswordCredential> credentials,
        Map<IncidentId, Incident> incidents,
        List<IncidentComment> comments,
        List<AuditEvent> auditEvents,
        Map<SloTargetVersionId, SloTargetVersion> sloTargetVersions) {
    LocalApplicationState {
        accounts = Map.copyOf(new LinkedHashMap<>(accounts));
        credentials = Map.copyOf(new LinkedHashMap<>(credentials));
        incidents = Map.copyOf(new LinkedHashMap<>(incidents));
        comments = List.copyOf(comments);
        auditEvents = List.copyOf(auditEvents);
        sloTargetVersions = Map.copyOf(new LinkedHashMap<>(sloTargetVersions));
    }

    static LocalApplicationState empty() {
        return new LocalApplicationState(Map.of(), Map.of(), Map.of(), List.of(), List.of(), Map.of());
    }
}

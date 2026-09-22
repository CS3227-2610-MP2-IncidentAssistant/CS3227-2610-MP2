package com.company.incidentdesk.persistence;

import java.util.List;
import java.util.Optional;

import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;

/** Append-only audit event repository without update or delete operations. */
public interface AuditRepository {
    void append(AuditEvent event);

    Optional<AuditEvent> findById(AuditEventId eventId);

    List<AuditEvent> find(AuditQuery query, AuditSortDirection direction);
}

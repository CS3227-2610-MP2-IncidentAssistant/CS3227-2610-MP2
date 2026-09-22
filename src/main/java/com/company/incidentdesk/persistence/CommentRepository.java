package com.company.incidentdesk.persistence;

import java.util.List;

import com.company.incidentdesk.domain.incident.IncidentId;

/** Append-only repository contract for future incident-comment domain records. */
public interface CommentRepository<I, C> extends AppendOnlyRepository<I, C> {
    List<C> findByIncidentId(IncidentId incidentId);
}

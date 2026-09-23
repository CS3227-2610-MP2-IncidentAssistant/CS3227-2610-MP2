package com.company.incidentdesk.persistence;

import java.util.List;

import com.company.incidentdesk.application.incident.IncidentMutation;
import com.company.incidentdesk.domain.comment.IncidentComment;
import com.company.incidentdesk.domain.incident.IncidentId;

/** One incident data source for queries and atomic audited mutations. */
public interface IncidentStore extends IncidentRepository, AuditedMutationStore<IncidentMutation> {
    List<IncidentComment> findCommentsByIncidentId(IncidentId incidentId);
}

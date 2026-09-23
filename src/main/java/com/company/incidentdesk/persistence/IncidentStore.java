package com.company.incidentdesk.persistence;

import com.company.incidentdesk.application.incident.IncidentMutation;

/** One incident data source for queries and atomic audited mutations. */
public interface IncidentStore extends IncidentRepository, AuditedMutationStore<IncidentMutation> {
}

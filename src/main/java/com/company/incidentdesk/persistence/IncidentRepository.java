package com.company.incidentdesk.persistence;

import java.util.List;
import java.util.Optional;

import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentId;

/** Storage-independent incident repository; callers must authorize every result. */
public interface IncidentRepository {
    void create(Incident incident);

    void update(Incident incident);

    Optional<Incident> findById(IncidentId incidentId);

    List<Incident> find(IncidentQuery query, IncidentSort sort);

    /** Finds incidents using the shared advanced-search criteria. */
    List<Incident> find(IncidentQuery query, IncidentSearchCriteria criteria);
}

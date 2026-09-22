package com.company.incidentdesk.persistence;

import java.util.List;

import com.company.incidentdesk.domain.incident.IncidentCategory;

/** Append-only repository contract for future versioned SLO configuration records. */
public interface SloConfigurationRepository<I, S> extends AppendOnlyRepository<I, S> {
    List<S> findByCategory(IncidentCategory category);
}

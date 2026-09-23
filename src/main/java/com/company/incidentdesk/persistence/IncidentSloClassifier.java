package com.company.incidentdesk.persistence;

import com.company.incidentdesk.domain.incident.Incident;

/** Supplies SLO state from configured, prospective SLO evaluation. */
@FunctionalInterface
public interface IncidentSloClassifier {
    IncidentSloState classify(Incident incident);
}

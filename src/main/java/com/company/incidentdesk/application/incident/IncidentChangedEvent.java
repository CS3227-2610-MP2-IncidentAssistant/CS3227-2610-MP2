package com.company.incidentdesk.application.incident;

import java.util.Objects;

import com.company.incidentdesk.domain.incident.IncidentAction;
import com.company.incidentdesk.domain.incident.IncidentId;

/** Minimal post-commit event that does not expose incident content or reporter identity. */
public record IncidentChangedEvent(IncidentId incidentId, IncidentAction action) {
    public IncidentChangedEvent {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(action, "action");
    }
}

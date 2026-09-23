package com.company.incidentdesk.application.incident;

import java.util.Objects;

import com.company.incidentdesk.application.event.ApplicationEvent;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.IncidentAction;
import com.company.incidentdesk.domain.incident.IncidentId;

/** Minimal post-commit event that does not expose incident content or reporter identity. */
public record IncidentChangedEvent(
        IncidentId incidentId,
        IncidentAction action,
        AccountId actorId) implements ApplicationEvent {
    public IncidentChangedEvent {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actorId, "actorId");
    }
}

package com.company.incidentdesk.application.incident;

/** Publishes incident notifications after the corresponding persistence commit succeeds. */
@FunctionalInterface
public interface IncidentEventPublisher {
    IncidentEventPublisher NO_OP = ignored -> { };

    void publish(IncidentChangedEvent event);
}

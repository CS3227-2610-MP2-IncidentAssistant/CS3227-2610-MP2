package com.company.incidentdesk.application.event;

/** Publishes post-commit application events. */
@FunctionalInterface
public interface ApplicationEventPublisher {
    ApplicationEventPublisher NO_OP = ignored -> { };

    void publish(ApplicationEvent event);
}

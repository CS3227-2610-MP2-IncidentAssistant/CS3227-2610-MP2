package com.company.incidentdesk.application.event;

import java.util.function.Consumer;

/** Process-local event publisher with lifecycle-safe subscriptions. */
public interface ApplicationEventBus extends ApplicationEventPublisher {
    <E extends ApplicationEvent> Subscription subscribe(Class<E> eventType, Consumer<E> subscriber);
}

package com.company.incidentdesk.application.event;

/** Removable event subscription. Closing an already closed subscription is harmless. */
@FunctionalInterface
public interface Subscription extends AutoCloseable {
    @Override
    void close();
}

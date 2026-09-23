package com.company.incidentdesk.application.event;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

import javafx.application.Platform;

/** Thread-safe in-process event bus with configurable subscriber dispatch. */
public final class InProcessApplicationEventBus implements ApplicationEventBus {
    private final CopyOnWriteArraySet<RegisteredSubscriber<?>> subscribers = new CopyOnWriteArraySet<>();
    private final Consumer<Runnable> dispatcher;
    private final Consumer<RuntimeException> errorHandler;

    public InProcessApplicationEventBus(
            Consumer<Runnable> dispatcher,
            Consumer<RuntimeException> errorHandler) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

    /** Creates a synchronous bus, useful for non-UI consumers and deterministic tests. */
    public static InProcessApplicationEventBus synchronous() {
        return new InProcessApplicationEventBus(Runnable::run, ignored -> { });
    }

    /** Creates a bus whose subscribers run on the JavaFX application thread. */
    public static InProcessApplicationEventBus onJavaFxThread(Consumer<RuntimeException> errorHandler) {
        return new InProcessApplicationEventBus(Platform::runLater, errorHandler);
    }

    @Override
    public <E extends ApplicationEvent> Subscription subscribe(
            Class<E> eventType,
            Consumer<E> subscriber) {
        RegisteredSubscriber<E> registration = new RegisteredSubscriber<>(eventType, subscriber);
        subscribers.add(registration);
        return () -> subscribers.remove(registration);
    }

    @Override
    public void publish(ApplicationEvent event) {
        ApplicationEvent requiredEvent = Objects.requireNonNull(event, "event");
        for (RegisteredSubscriber<?> subscriber : subscribers) {
            if (subscriber.accepts(requiredEvent)) {
                dispatcher.accept(() -> notifySubscriber(subscriber, requiredEvent));
            }
        }
    }

    private void notifySubscriber(RegisteredSubscriber<?> subscriber, ApplicationEvent event) {
        try {
            subscriber.notify(event);
        } catch (RuntimeException exception) {
            errorHandler.accept(exception);
        }
    }

    private record RegisteredSubscriber<E extends ApplicationEvent>(
            Class<E> eventType,
            Consumer<E> subscriber) {
        private RegisteredSubscriber {
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(subscriber, "subscriber");
        }

        boolean accepts(ApplicationEvent event) {
            return eventType.isInstance(event);
        }

        void notify(ApplicationEvent event) {
            subscriber.accept(eventType.cast(event));
        }
    }
}

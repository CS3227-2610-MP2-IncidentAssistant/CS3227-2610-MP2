package com.company.incidentdesk.application.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import javafx.application.Platform;

class InProcessApplicationEventBusTest {
    @Test
    void supportsIdempotentRegistrationAndUnsubscription() {
        InProcessApplicationEventBus bus = InProcessApplicationEventBus.synchronous();
        List<TestEvent> received = new ArrayList<>();
        Consumer<TestEvent> subscriber = received::add;
        Subscription first = bus.subscribe(TestEvent.class, subscriber);
        bus.subscribe(TestEvent.class, subscriber);

        bus.publish(new TestEvent("one"));
        first.close();
        first.close();
        bus.publish(new TestEvent("two"));

        assertEquals(List.of(new TestEvent("one")), received);
    }

    @Test
    void isolatesSubscriberFailureFromPublisherAndOtherSubscribers() {
        List<RuntimeException> failures = new ArrayList<>();
        InProcessApplicationEventBus bus = new InProcessApplicationEventBus(Runnable::run, failures::add);
        List<TestEvent> received = new ArrayList<>();
        bus.subscribe(TestEvent.class, ignored -> {
            throw new IllegalStateException("subscriber failed");
        });
        bus.subscribe(TestEvent.class, received::add);

        bus.publish(new TestEvent("delivered"));

        assertEquals(1, failures.size());
        assertEquals(List.of(new TestEvent("delivered")), received);
    }

    @Test
    void javaFxDispatcherRunsSubscriberOnApplicationThread() throws InterruptedException {
        startJavaFx();
        InProcessApplicationEventBus bus = InProcessApplicationEventBus.onJavaFxThread(ignored -> { });
        CountDownLatch delivered = new CountDownLatch(1);
        List<Boolean> applicationThread = new ArrayList<>();
        bus.subscribe(TestEvent.class, ignored -> {
            applicationThread.add(Platform.isFxApplicationThread());
            delivered.countDown();
        });

        bus.publish(new TestEvent("delivered"));

        assertTrue(delivered.await(10, TimeUnit.SECONDS));
        assertEquals(List.of(true), applicationThread);
    }

    private static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    private record TestEvent(String value) implements ApplicationEvent {
    }
}

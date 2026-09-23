package com.company.incidentdesk.ui.shared.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.notification.Notification;
import com.company.incidentdesk.application.notification.NotificationId;
import com.company.incidentdesk.application.notification.NotificationInbox;
import com.company.incidentdesk.application.notification.NotificationType;
import com.company.incidentdesk.domain.account.AccountId;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

class NotificationCenterTest {
    private static final Instant NOW = Instant.parse("2026-09-23T08:00:00Z");
    private static final AccountId RECIPIENT = new AccountId(new UUID(0, 1));

    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    void openingTrayRendersNotificationAndClearsDisplayedUnseenCount() throws Exception {
        runOnJavaFx(() -> {
            NotificationInbox inbox = new NotificationInbox();
            NotificationCenter center = new NotificationCenter(inbox, RECIPIENT);
            inbox.add(new Notification(
                    new NotificationId(new UUID(1, 1)), RECIPIENT, NotificationType.INCIDENT,
                    Optional.empty(), "An incident was resolved.", NOW, 1));

            Label badge = (Label) center.lookup("#notification-unseen-count");
            Button toggle = (Button) center.lookup("#notification-toggle");
            assertEquals("1", badge.getText());
            assertTrue(badge.isVisible());

            toggle.fire();

            assertEquals(1, center.displayedUnseenMarkerCount());
            assertFalse(badge.isVisible());
            assertEquals(0, inbox.snapshot(RECIPIENT).unseenCount());
            toggle.fire();
            toggle.fire();
            assertEquals(0, center.displayedUnseenMarkerCount());
            center.close();
        });
    }

    private static void runOnJavaFx(Runnable assertions) throws Exception {
        FutureTask<Void> task = new FutureTask<>(assertions, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}

package com.company.incidentdesk.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;

class NotificationInboxTest {
    private static final AccountId RECIPIENT = new AccountId(new UUID(0, 1));

    @Test
    void seenWatermarkDoesNotAcknowledgeLaterArrival() {
        NotificationInbox inbox = new NotificationInbox();
        inbox.add(notification(1));
        long displayedThrough = inbox.snapshot(RECIPIENT).latestSequence();
        inbox.add(notification(2));

        inbox.markSeenThrough(RECIPIENT, displayedThrough);

        assertEquals(1, inbox.snapshot(RECIPIENT).unseenCount());
    }

    @Test
    void retentionIsBoundedAndPartitionedByRecipient() {
        NotificationInbox inbox = new NotificationInbox(2);
        inbox.add(notification(1));
        inbox.add(notification(2));
        inbox.add(notification(3));

        assertEquals(2, inbox.snapshot(RECIPIENT).notifications().size());
        assertEquals(0, inbox.snapshot(new AccountId(new UUID(0, 99))).notifications().size());
    }

    private static Notification notification(long id) {
        return new Notification(
                new NotificationId(new UUID(0, id)), RECIPIENT, NotificationType.ACCOUNT,
                Optional.empty(), "Notification " + id, Instant.EPOCH, 1);
    }
}

package com.company.incidentdesk.application.notification;

import java.util.List;
import java.util.Objects;

/** Immutable view of a recipient's retained and unseen notifications. */
public record NotificationSnapshot(List<Notification> notifications, long lastSeenSequence) {
    public NotificationSnapshot {
        notifications = List.copyOf(Objects.requireNonNull(notifications, "notifications"));
        if (lastSeenSequence < 0) {
            throw new IllegalArgumentException("lastSeenSequence must not be negative");
        }
    }

    public long latestSequence() {
        return notifications.isEmpty() ? lastSeenSequence : notifications.getLast().sequence();
    }

    public long unseenCount() {
        return notifications.stream().filter(notification -> notification.sequence() > lastSeenSequence).count();
    }
}

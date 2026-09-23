package com.company.incidentdesk.application.notification;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for one process-local notification. */
public record NotificationId(UUID value) {
    public NotificationId {
        Objects.requireNonNull(value, "value");
    }
}

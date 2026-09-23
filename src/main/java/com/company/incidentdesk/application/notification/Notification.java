package com.company.incidentdesk.application.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.IncidentId;

/** Authorized, presentation-safe notification retained for one recipient. */
public record Notification(
        NotificationId id,
        AccountId recipientId,
        NotificationType type,
        Optional<IncidentId> incidentId,
        String message,
        Instant createdAt,
        long sequence) {
    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(createdAt, "createdAt");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
    }
}

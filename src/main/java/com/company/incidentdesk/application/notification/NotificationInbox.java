package com.company.incidentdesk.application.notification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.company.incidentdesk.application.event.Subscription;
import com.company.incidentdesk.domain.account.AccountId;

/** Bounded, process-local notification state partitioned by recipient. */
public final class NotificationInbox {
    public static final int DEFAULT_RETENTION_LIMIT = 100;

    private final int retentionLimit;
    private final Map<AccountId, List<Notification>> notificationsByRecipient = new HashMap<>();
    private final Map<AccountId, Long> lastSeenByRecipient = new HashMap<>();
    private final Map<AccountId, Set<Consumer<NotificationSnapshot>>> listenersByRecipient = new HashMap<>();
    private long nextSequence = 1;

    public NotificationInbox() {
        this(DEFAULT_RETENTION_LIMIT);
    }

    public NotificationInbox(int retentionLimit) {
        if (retentionLimit < 1) {
            throw new IllegalArgumentException("retentionLimit must be positive");
        }
        this.retentionLimit = retentionLimit;
    }

    public void add(Notification notification) {
        AccountId recipientId;
        NotificationSnapshot snapshot;
        Set<Consumer<NotificationSnapshot>> listeners;
        synchronized (this) {
            Notification supplied = Objects.requireNonNull(notification, "notification");
            recipientId = supplied.recipientId();
            Notification sequenced = new Notification(
                    supplied.id(), supplied.recipientId(), supplied.type(), supplied.incidentId(),
                    supplied.message(), supplied.createdAt(), nextSequence++);
            List<Notification> retained = new ArrayList<>(
                    notificationsByRecipient.getOrDefault(recipientId, List.of()));
            retained.add(sequenced);
            if (retained.size() > retentionLimit) {
                retained = new ArrayList<>(retained.subList(retained.size() - retentionLimit, retained.size()));
            }
            notificationsByRecipient.put(recipientId, List.copyOf(retained));
            snapshot = snapshot(recipientId);
            listeners = Set.copyOf(listenersByRecipient.getOrDefault(recipientId, Set.of()));
        }
        listeners.forEach(listener -> listener.accept(snapshot));
    }

    public synchronized NotificationSnapshot snapshot(AccountId recipientId) {
        AccountId requiredId = Objects.requireNonNull(recipientId, "recipientId");
        return new NotificationSnapshot(
                notificationsByRecipient.getOrDefault(requiredId, List.of()),
                lastSeenByRecipient.getOrDefault(requiredId, 0L));
    }

    public void markSeenThrough(AccountId recipientId, long sequence) {
        NotificationSnapshot snapshot;
        Set<Consumer<NotificationSnapshot>> listeners;
        synchronized (this) {
            AccountId requiredId = Objects.requireNonNull(recipientId, "recipientId");
            long latest = snapshot(requiredId).latestSequence();
            long current = lastSeenByRecipient.getOrDefault(requiredId, 0L);
            lastSeenByRecipient.put(requiredId, Math.max(current, Math.min(sequence, latest)));
            snapshot = snapshot(requiredId);
            listeners = Set.copyOf(listenersByRecipient.getOrDefault(requiredId, Set.of()));
        }
        listeners.forEach(listener -> listener.accept(snapshot));
    }

    public Subscription subscribe(AccountId recipientId, Consumer<NotificationSnapshot> listener) {
        AccountId requiredId = Objects.requireNonNull(recipientId, "recipientId");
        Consumer<NotificationSnapshot> requiredListener = Objects.requireNonNull(listener, "listener");
        synchronized (this) {
            listenersByRecipient.computeIfAbsent(requiredId, ignored -> new LinkedHashSet<>())
                    .add(requiredListener);
        }
        requiredListener.accept(snapshot(requiredId));
        return () -> removeListener(requiredId, requiredListener);
    }

    private synchronized void removeListener(
            AccountId recipientId,
            Consumer<NotificationSnapshot> listener) {
        Set<Consumer<NotificationSnapshot>> listeners = listenersByRecipient.get(recipientId);
        if (listeners == null) {
            return;
        }
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            listenersByRecipient.remove(recipientId);
        }
    }
}

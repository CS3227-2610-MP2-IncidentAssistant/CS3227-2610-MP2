package com.company.incidentdesk.persistence.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.persistence.AuditQuery;
import com.company.incidentdesk.persistence.AuditRepository;
import com.company.incidentdesk.persistence.AuditSortDirection;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** In-memory append-only audit repository with deterministic filtering and ordering. */
public final class InMemoryAuditRepository implements AuditRepository {
    private final Map<AuditEventId, AuditEvent> eventsById = new LinkedHashMap<>();

    @Override
    public synchronized void append(AuditEvent event) {
        AuditEvent requiredEvent = Objects.requireNonNull(event, "event");
        if (eventsById.containsKey(requiredEvent.id())) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "audit event already exists");
        }
        eventsById.put(requiredEvent.id(), requiredEvent);
    }

    @Override
    public synchronized Optional<AuditEvent> findById(AuditEventId eventId) {
        return Optional.ofNullable(eventsById.get(Objects.requireNonNull(eventId, "eventId")));
    }

    @Override
    public synchronized List<AuditEvent> find(AuditQuery query, AuditSortDirection direction) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(direction, "direction");
        java.util.Comparator<AuditEvent> comparator = AuditEvent.CHRONOLOGICAL_ORDER;
        if (direction == AuditSortDirection.NEWEST_FIRST) {
            comparator = comparator.reversed();
        }
        return eventsById.values().stream()
                .filter(event -> matches(query, event))
                .sorted(comparator)
                .toList();
    }

    private static boolean matches(AuditQuery query, AuditEvent event) {
        boolean afterStart = query.fromInclusive()
                .map(start -> !event.occurredAt().isBefore(start))
                .orElse(true);
        boolean beforeEnd = query.toExclusive()
                .map(end -> event.occurredAt().isBefore(end))
                .orElse(true);
        boolean actorMatches = query.actorId()
                .map(actorId -> event.actor().visibility() == AuditActorVisibility.STANDARD
                        && event.actor().accountId().equals(actorId))
                .orElse(true);
        boolean actionMatches = query.actions().isEmpty() || query.actions().contains(event.action());
        boolean targetMatches = query.target().map(event.target()::equals).orElse(true);
        return afterStart && beforeEnd && actorMatches && actionMatches && targetMatches;
    }
}

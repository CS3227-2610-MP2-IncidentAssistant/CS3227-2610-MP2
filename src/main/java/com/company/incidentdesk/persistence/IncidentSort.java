package com.company.incidentdesk.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.incident.Incident;

/** Deterministic incident ordering with an ascending incident-ID tie-breaker. */
public record IncidentSort(IncidentSortField field, SortDirection direction) {
    /** Creates a deterministic incident sort. */
    public IncidentSort {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(direction, "direction");
    }

    public static IncidentSort queueOrder() {
        return new IncidentSort(IncidentSortField.QUEUE_ENTERED_AT, SortDirection.ASCENDING);
    }

    /** Returns a comparator that keeps missing timestamps last in either direction. */
    public Comparator<Incident> comparator() {
        return (left, right) -> {
            int primary = comparePrimary(left, right);
            if (primary != 0) {
                return primary;
            }
            return left.id().value().compareTo(right.id().value());
        };
    }

    private int comparePrimary(Incident left, Incident right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        return switch (field) {
        case CREATED_AT -> directional(left.createdAt().compareTo(right.createdAt()));
        case SUBMITTED_AT -> compareOptional(left.submittedAt(), right.submittedAt());
        case QUEUE_ENTERED_AT -> compareOptional(queueEnteredAt(left), queueEnteredAt(right));
        case TITLE -> directional(left.title().compareTo(right.title()));
        case STATUS -> directional(left.status().compareTo(right.status()));
        case CATEGORY -> directional(left.category().compareTo(right.category()));
        };
    }

    private int compareOptional(Optional<Instant> left, Optional<Instant> right) {
        if (left.isEmpty()) {
            return right.isEmpty() ? 0 : 1;
        }
        if (right.isEmpty()) {
            return -1;
        }
        return directional(left.orElseThrow().compareTo(right.orElseThrow()));
    }

    private int directional(int comparison) {
        return direction == SortDirection.ASCENDING ? comparison : -comparison;
    }

    private static Optional<Instant> queueEnteredAt(Incident incident) {
        return incident.currentCycle().map(cycle -> cycle.queueEnteredAt());
    }
}

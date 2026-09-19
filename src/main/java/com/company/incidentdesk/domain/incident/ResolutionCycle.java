package com.company.incidentdesk.domain.incident;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Queue, assignment, and resolution timestamps for one lifecycle cycle. */
public record ResolutionCycle(
        Instant queueEnteredAt,
        Optional<Instant> firstAssignedAt,
        Optional<Instant> latestAssignedAt,
        Optional<Resolution> resolution) {
    /**
     * Creates and validates a resolution cycle.
     *
     * @param queueEnteredAt UTC time the cycle entered its category queue
     * @param firstAssignedAt first assignment time for the cycle
     * @param latestAssignedAt most recent assignment time for the cycle
     * @param resolution completed resolution, when present
     */
    public ResolutionCycle {
        Objects.requireNonNull(queueEnteredAt, "queueEnteredAt");
        Objects.requireNonNull(firstAssignedAt, "firstAssignedAt");
        Objects.requireNonNull(latestAssignedAt, "latestAssignedAt");
        Objects.requireNonNull(resolution, "resolution");

        validateAssignmentTimes(queueEnteredAt, firstAssignedAt, latestAssignedAt);
        resolution.ifPresent(completed -> validateResolutionTime(latestAssignedAt, completed));
    }

    /**
     * Checks whether the cycle has been resolved.
     *
     * @return true when resolution information is present
     */
    public boolean isResolved() {
        return resolution.isPresent();
    }

    private static void validateAssignmentTimes(
            Instant queueEnteredAt,
            Optional<Instant> firstAssignedAt,
            Optional<Instant> latestAssignedAt) {
        if (firstAssignedAt.isPresent() != latestAssignedAt.isPresent()) {
            throw new IllegalArgumentException("assignment timestamps must both be present or absent");
        }
        firstAssignedAt.ifPresent(first -> requireNotBefore(first, queueEnteredAt, "firstAssignedAt"));
        latestAssignedAt.ifPresent(latest -> {
            requireNotBefore(latest, queueEnteredAt, "latestAssignedAt");
            firstAssignedAt.ifPresent(first -> requireNotBefore(latest, first, "latestAssignedAt"));
        });
    }

    private static void validateResolutionTime(
            Optional<Instant> latestAssignedAt,
            Resolution resolution) {
        Instant latestAssignment = latestAssignedAt.orElseThrow(
                () -> new IllegalArgumentException("resolution requires an assignment"));
        requireNotBefore(resolution.resolvedAt(), latestAssignment, "resolvedAt");
    }

    private static void requireNotBefore(Instant value, Instant earliest, String fieldName) {
        if (value.isBefore(earliest)) {
            throw new IllegalArgumentException(fieldName + " is earlier than its prerequisite timestamp");
        }
    }
}

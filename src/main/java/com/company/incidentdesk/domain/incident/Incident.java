package com.company.incidentdesk.domain.incident;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.account.AccountId;

/** Immutable canonical representation of an incident and its lifecycle history. */
public record Incident(
        IncidentId id,
        AccountId reporterId,
        String title,
        String description,
        IncidentCategory category,
        IncidentStatus status,
        boolean anonymous,
        Instant createdAt,
        Optional<Instant> submittedAt,
        Optional<Instant> withdrawnAt,
        Optional<AccountId> assigneeId,
        List<ResolutionCycle> resolutionCycles) {
    /**
     * Creates and validates an immutable incident snapshot.
     *
     * @param id stable incident identifier
     * @param reporterId internal owning reporter identifier
     * @param title title stripped before storage and required after draft
     * @param description description preserved as entered and required after draft
     * @param category incident category
     * @param status lifecycle status
     * @param anonymous whether reporter identity must be hidden from other users
     * @param createdAt application-generated UTC creation time
     * @param submittedAt original UTC submission time
     * @param withdrawnAt UTC withdrawal time, when withdrawn
     * @param assigneeId current responder, only while assigned
     * @param resolutionCycles immutable lifecycle-cycle history
     */
    public Incident(
            IncidentId id,
            AccountId reporterId,
            String title,
            String description,
            IncidentCategory category,
            IncidentStatus status,
            boolean anonymous,
            Instant createdAt,
            Optional<Instant> submittedAt,
            Optional<Instant> withdrawnAt,
            Optional<AccountId> assigneeId,
            List<ResolutionCycle> resolutionCycles) {
        this.id = Objects.requireNonNull(id, "id");
        this.reporterId = Objects.requireNonNull(reporterId, "reporterId");
        this.title = normalizeTitle(title);
        this.description = Objects.requireNonNull(description, "description");
        this.category = Objects.requireNonNull(category, "category");
        this.status = Objects.requireNonNull(status, "status");
        this.anonymous = anonymous;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.withdrawnAt = Objects.requireNonNull(withdrawnAt, "withdrawnAt");
        this.assigneeId = Objects.requireNonNull(assigneeId, "assigneeId");
        this.resolutionCycles = List.copyOf(Objects.requireNonNull(resolutionCycles, "resolutionCycles"));

        validateRequiredContent();
        validateTimeline();
        validateStatusState();
    }

    /**
     * Returns how many completed incidents were reopened into a new cycle.
     *
     * @return number of reopen events represented by the cycle history
     */
    public int reopenCount() {
        return Math.max(0, resolutionCycles.size() - 1);
    }

    /**
     * Returns the latest queue/resolution cycle.
     *
     * @return latest cycle, or empty for a draft
     */
    public Optional<ResolutionCycle> currentCycle() {
        if (resolutionCycles.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(resolutionCycles.getLast());
    }

    private static String normalizeTitle(String title) {
        Objects.requireNonNull(title, "title");
        return title.strip();
    }

    private void validateRequiredContent() {
        if (status == IncidentStatus.DRAFT) {
            return;
        }
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    private void validateTimeline() {
        if (submittedAt.isPresent()) {
            requireNotBefore(submittedAt.orElseThrow(), createdAt, "submittedAt");
        }
        validateCycleSequence();
        if (withdrawnAt.isPresent()) {
            Instant submission = submittedAt.orElseThrow(
                    () -> new IllegalArgumentException("withdrawnAt requires submittedAt"));
            requireNotBefore(withdrawnAt.orElseThrow(), submission, "withdrawnAt");
            currentCycle().ifPresent(cycle -> {
                Instant withdrawal = withdrawnAt.orElseThrow();
                requireNotBefore(withdrawal, cycle.queueEnteredAt(), "withdrawnAt");
                cycle.latestAssignedAt().ifPresent(latest ->
                        requireNotBefore(withdrawal, latest, "withdrawnAt"));
            });
        }
    }

    private void validateCycleSequence() {
        if (resolutionCycles.isEmpty()) {
            return;
        }

        Instant submission = submittedAt.orElseThrow(
                () -> new IllegalArgumentException("resolution cycles require submittedAt"));
        if (!resolutionCycles.getFirst().queueEnteredAt().equals(submission)) {
            throw new IllegalArgumentException("first queue entry must equal submittedAt");
        }

        ResolutionCycle previousCycle = null;
        for (ResolutionCycle cycle : resolutionCycles) {
            requireNotBefore(cycle.queueEnteredAt(), submission, "queueEnteredAt");
            if (previousCycle != null) {
                Resolution previousResolution = previousCycle.resolution().orElseThrow(
                        () -> new IllegalArgumentException("only the latest cycle may be unresolved"));
                requireNotBefore(cycle.queueEnteredAt(), previousResolution.resolvedAt(), "queueEnteredAt");
            }
            previousCycle = cycle;
        }
    }

    private void validateStatusState() {
        switch (status) {
        case DRAFT -> validateDraft();
        case SUBMITTED -> validateOpenIncident(false);
        case ASSIGNED -> validateOpenIncident(true);
        case RESOLVED -> validateResolved();
        case WITHDRAWN -> validateWithdrawn();
        }
    }

    private void validateDraft() {
        requireAbsent(submittedAt, "submittedAt");
        requireAbsent(withdrawnAt, "withdrawnAt");
        requireAbsent(assigneeId, "assigneeId");
        if (!resolutionCycles.isEmpty()) {
            throw new IllegalArgumentException("draft incidents cannot have resolution cycles");
        }
    }

    private void validateOpenIncident(boolean assigned) {
        requirePresent(submittedAt, "submittedAt");
        requireAbsent(withdrawnAt, "withdrawnAt");
        ResolutionCycle currentCycle = requireCurrentCycle();
        if (currentCycle.isResolved()) {
            throw new IllegalArgumentException("open incidents require an unresolved current cycle");
        }

        if (assigned) {
            requirePresent(assigneeId, "assigneeId");
            requirePresent(currentCycle.firstAssignedAt(), "firstAssignedAt");
            requirePresent(currentCycle.latestAssignedAt(), "latestAssignedAt");
        } else {
            requireAbsent(assigneeId, "assigneeId");
        }
    }

    private void validateResolved() {
        requirePresent(submittedAt, "submittedAt");
        requireAbsent(withdrawnAt, "withdrawnAt");
        requireAbsent(assigneeId, "assigneeId");
        if (!requireCurrentCycle().isResolved()) {
            throw new IllegalArgumentException("resolved incidents require a completed current cycle");
        }
    }

    private void validateWithdrawn() {
        requirePresent(submittedAt, "submittedAt");
        requirePresent(withdrawnAt, "withdrawnAt");
        requireAbsent(assigneeId, "assigneeId");
        if (requireCurrentCycle().isResolved()) {
            throw new IllegalArgumentException("withdrawn incidents cannot have a resolved current cycle");
        }
    }

    private ResolutionCycle requireCurrentCycle() {
        return currentCycle().orElseThrow(
                () -> new IllegalArgumentException(status + " incidents require a resolution cycle"));
    }

    private static void requirePresent(Optional<?> value, String fieldName) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private static void requireAbsent(Optional<?> value, String fieldName) {
        if (value.isPresent()) {
            throw new IllegalArgumentException(fieldName + " must be absent");
        }
    }

    private static void requireNotBefore(Instant value, Instant earliest, String fieldName) {
        if (value.isBefore(earliest)) {
            throw new IllegalArgumentException(fieldName + " is earlier than its prerequisite timestamp");
        }
    }
}

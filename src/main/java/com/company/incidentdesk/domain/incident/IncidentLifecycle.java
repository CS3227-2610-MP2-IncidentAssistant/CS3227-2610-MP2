package com.company.incidentdesk.domain.incident;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.company.incidentdesk.domain.account.AccountId;

/** Applies canonical incident transitions without exposing caller-controlled status changes. */
public final class IncidentLifecycle {
    private final Clock clock;

    /**
     * Creates lifecycle operations driven by an application clock.
     *
     * @param clock source of transition timestamps
     */
    public IncidentLifecycle(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Creates an incomplete or complete draft owned by a reporter. */
    public Incident saveDraft(
            IncidentId id,
            AccountId reporterId,
            String title,
            String description,
            IncidentCategory category,
            boolean anonymous) {
        return new Incident(
                id,
                reporterId,
                title,
                description,
                category,
                IncidentStatus.DRAFT,
                anonymous,
                clock.instant(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }

    /** Creates and directly submits an incident. */
    public Incident submit(
            IncidentId id,
            AccountId reporterId,
            String title,
            String description,
            IncidentCategory category,
            boolean anonymous) {
        Instant submittedAt = clock.instant();
        return new Incident(
                id,
                reporterId,
                title,
                description,
                category,
                IncidentStatus.SUBMITTED,
                anonymous,
                submittedAt,
                Optional.of(submittedAt),
                Optional.empty(),
                Optional.empty(),
                List.of(queuedCycle(submittedAt)));
    }

    /** Submits an existing draft while preserving its creation time. */
    public Incident submit(Incident draft) {
        requireStatus(draft, IncidentAction.SUBMIT, IncidentStatus.DRAFT);
        Instant submittedAt = clock.instant();
        return copy(
                draft,
                draft.title(),
                draft.description(),
                draft.category(),
                draft.anonymous(),
                IncidentStatus.SUBMITTED,
                Optional.of(submittedAt),
                Optional.empty(),
                Optional.empty(),
                List.of(queuedCycle(submittedAt)));
    }

    /** Edits mutable report content without changing queue or lifecycle timestamps. */
    public Incident edit(
            Incident incident,
            String title,
            String description,
            IncidentCategory category,
            boolean anonymous) {
        requireStatus(incident, IncidentAction.EDIT, IncidentStatus.DRAFT, IncidentStatus.SUBMITTED);
        return copy(
                incident,
                title,
                description,
                category,
                anonymous,
                incident.status(),
                incident.submittedAt(),
                incident.withdrawnAt(),
                incident.assigneeId(),
                incident.resolutionCycles());
    }

    /** Withdraws an unassigned submitted incident. */
    public Incident withdraw(Incident incident) {
        requireStatus(incident, IncidentAction.WITHDRAW, IncidentStatus.SUBMITTED);
        return copy(
                incident,
                incident.title(),
                incident.description(),
                incident.category(),
                incident.anonymous(),
                IncidentStatus.WITHDRAWN,
                incident.submittedAt(),
                Optional.of(clock.instant()),
                Optional.empty(),
                incident.resolutionCycles());
    }

    /** Claims an unassigned submitted incident for a responder. */
    public Incident claim(Incident incident, AccountId responderId) {
        return assignSubmitted(incident, responderId, IncidentAction.CLAIM);
    }

    /** Assigns an unassigned submitted incident to a responder. */
    public Incident assign(Incident incident, AccountId responderId) {
        return assignSubmitted(incident, responderId, IncidentAction.ASSIGN);
    }

    /**
     * Assigns or replaces the current assignee, setting the first assignment time only when this is
     * the incident's first assignment within its current cycle.
     */
    public Incident reassign(Incident incident, AccountId responderId) {
        requireStatus(incident, IncidentAction.REASSIGN, IncidentStatus.SUBMITTED, IncidentStatus.ASSIGNED);
        Objects.requireNonNull(responderId, "responderId");
        Instant reassignedAt = clock.instant();
        ResolutionCycle cycle = incident.currentCycle().orElseThrow();
        Optional<Instant> firstAssignedAt = cycle.firstAssignedAt().or(() -> Optional.of(reassignedAt));
        ResolutionCycle reassignedCycle = new ResolutionCycle(
                cycle.queueEnteredAt(),
                firstAssignedAt,
                Optional.of(reassignedAt),
                Optional.empty());
        return copyWithCurrentCycle(incident, IncidentStatus.ASSIGNED, Optional.of(responderId), reassignedCycle);
    }

    /** Resolves an assigned incident and retains the responder assigned at resolution time. */
    public Incident resolve(Incident incident, AccountId resolvedBy, String remarks) {
        requireStatus(incident, IncidentAction.RESOLVE, IncidentStatus.ASSIGNED);
        AccountId actorId = Objects.requireNonNull(resolvedBy, "resolvedBy");
        AccountId responderId = incident.assigneeId().orElseThrow();
        ResolutionCycle cycle = incident.currentCycle().orElseThrow();
        Resolution resolution = new Resolution(remarks, clock.instant(), actorId, responderId);
        ResolutionCycle resolvedCycle = new ResolutionCycle(
                cycle.queueEnteredAt(),
                cycle.firstAssignedAt(),
                cycle.latestAssignedAt(),
                Optional.of(resolution));
        return copyWithCurrentCycle(incident, IncidentStatus.RESOLVED, Optional.empty(), resolvedCycle);
    }

    /** Returns an assigned incident to its existing queue position. */
    public Incident handoff(Incident incident) {
        requireStatus(incident, IncidentAction.HANDOFF, IncidentStatus.ASSIGNED);
        return copy(
                incident,
                incident.title(),
                incident.description(),
                incident.category(),
                incident.anonymous(),
                IncidentStatus.SUBMITTED,
                incident.submittedAt(),
                Optional.empty(),
                Optional.empty(),
                incident.resolutionCycles());
    }

    /** Reopens a resolved incident and produces its mandatory explanatory follow-up. */
    public ReopenTransition reopen(Incident incident, AccountId reporterId, String explanationText) {
        requireStatus(incident, IncidentAction.REOPEN, IncidentStatus.RESOLVED);
        AccountId authorId = Objects.requireNonNull(reporterId, "reporterId");
        Instant reopenedAt = clock.instant();
        List<ResolutionCycle> cycles = new ArrayList<>(incident.resolutionCycles());
        cycles.add(queuedCycle(reopenedAt));
        Incident reopened = copy(
                incident,
                incident.title(),
                incident.description(),
                incident.category(),
                incident.anonymous(),
                IncidentStatus.SUBMITTED,
                incident.submittedAt(),
                Optional.empty(),
                Optional.empty(),
                cycles);
        ReopenExplanation explanation = new ReopenExplanation(
                authorId,
                explanationText,
                reopenedAt,
                cycles.size());
        return new ReopenTransition(reopened, explanation);
    }

    private Incident assignSubmitted(Incident incident, AccountId responderId, IncidentAction action) {
        requireStatus(incident, action, IncidentStatus.SUBMITTED);
        Objects.requireNonNull(responderId, "responderId");
        Instant assignedAt = clock.instant();
        ResolutionCycle cycle = incident.currentCycle().orElseThrow();
        Optional<Instant> firstAssignedAt = cycle.firstAssignedAt().or(() -> Optional.of(assignedAt));
        ResolutionCycle assignedCycle = new ResolutionCycle(
                cycle.queueEnteredAt(),
                firstAssignedAt,
                Optional.of(assignedAt),
                Optional.empty());
        return copyWithCurrentCycle(incident, IncidentStatus.ASSIGNED, Optional.of(responderId), assignedCycle);
    }

    private static Incident copyWithCurrentCycle(
            Incident incident,
            IncidentStatus status,
            Optional<AccountId> assigneeId,
            ResolutionCycle currentCycle) {
        List<ResolutionCycle> cycles = new ArrayList<>(incident.resolutionCycles());
        cycles.set(cycles.size() - 1, currentCycle);
        return copy(
                incident,
                incident.title(),
                incident.description(),
                incident.category(),
                incident.anonymous(),
                status,
                incident.submittedAt(),
                Optional.empty(),
                assigneeId,
                cycles);
    }

    private static Incident copy(
            Incident incident,
            String title,
            String description,
            IncidentCategory category,
            boolean anonymous,
            IncidentStatus status,
            Optional<Instant> submittedAt,
            Optional<Instant> withdrawnAt,
            Optional<AccountId> assigneeId,
            List<ResolutionCycle> resolutionCycles) {
        Objects.requireNonNull(incident, "incident");
        return new Incident(
                incident.id(),
                incident.reporterId(),
                title,
                description,
                category,
                status,
                anonymous,
                incident.createdAt(),
                submittedAt,
                withdrawnAt,
                assigneeId,
                resolutionCycles);
    }

    private static ResolutionCycle queuedCycle(Instant queueEnteredAt) {
        return new ResolutionCycle(
                queueEnteredAt,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static void requireStatus(
            Incident incident,
            IncidentAction action,
            IncidentStatus... allowedStatuses) {
        Objects.requireNonNull(incident, "incident");
        Set<IncidentStatus> allowed = Set.of(allowedStatuses);
        if (!allowed.contains(incident.status())) {
            throw new InvalidIncidentTransitionException(action, incident.status(), allowed);
        }
    }
}

package com.company.incidentdesk.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentStatus;

/** Immutable, role-independent incident search and filter criteria. */
public record IncidentSearchCriteria(
        String text,
        Set<IncidentCategory> categories,
        Set<IncidentStatus> statuses,
        AssignmentState assignmentState,
        Optional<AccountId> reporterId,
        Optional<AccountId> responderId,
        Optional<Instant> createdFrom,
        Optional<Instant> createdThrough,
        Set<IncidentSloState> sloStates,
        IncidentSort sort) {
    public IncidentSearchCriteria {
        text = Objects.requireNonNull(text, "text").strip();
        categories = Set.copyOf(Objects.requireNonNull(categories, "categories"));
        statuses = Set.copyOf(Objects.requireNonNull(statuses, "statuses"));
        Objects.requireNonNull(assignmentState, "assignmentState");
        Objects.requireNonNull(reporterId, "reporterId");
        Objects.requireNonNull(responderId, "responderId");
        Objects.requireNonNull(createdFrom, "createdFrom");
        Objects.requireNonNull(createdThrough, "createdThrough");
        sloStates = Set.copyOf(Objects.requireNonNull(sloStates, "sloStates"));
        Objects.requireNonNull(sort, "sort");
        if (createdFrom.isPresent() && createdThrough.isPresent()
                && createdFrom.orElseThrow().isAfter(createdThrough.orElseThrow())) {
            throw new IllegalArgumentException("createdFrom must not be after createdThrough");
        }
    }

    public static IncidentSearchCriteria defaults() {
        return new IncidentSearchCriteria("", Set.of(), Set.of(), AssignmentState.ANY,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Set.of(),
                new IncidentSort(IncidentSortField.CREATED_AT, SortDirection.DESCENDING));
    }

    /** Removes identity filters which are not available to reporter-facing searches. */
    public IncidentSearchCriteria withoutIdentityFilters() {
        return new IncidentSearchCriteria(text, categories, statuses, assignmentState,
                Optional.empty(), Optional.empty(), createdFrom, createdThrough, sloStates, sort);
    }
}

package com.company.incidentdesk.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.IncidentCategory;

/** Validated storage query for role-specific incident lists. */
public record IncidentQuery(
        IncidentQueryScope scope,
        Optional<AccountId> accountId,
        Set<IncidentCategory> categories) {
    /** Creates a query with fields consistent with its scope. */
    public IncidentQuery {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(accountId, "accountId");
        categories = Set.copyOf(Objects.requireNonNull(categories, "categories"));
        validateScope(scope, accountId, categories);
    }

    public static IncidentQuery reporterOwned(AccountId reporterId) {
        return new IncidentQuery(
                IncidentQueryScope.REPORTER_OWNED,
                Optional.of(Objects.requireNonNull(reporterId, "reporterId")),
                Set.of());
    }

    public static IncidentQuery responderEligibleUnassigned(Set<IncidentCategory> categories) {
        return new IncidentQuery(
                IncidentQueryScope.RESPONDER_ELIGIBLE_UNASSIGNED,
                Optional.empty(),
                categories);
    }

    public static IncidentQuery responderAssigned(AccountId responderId) {
        return new IncidentQuery(
                IncidentQueryScope.RESPONDER_ASSIGNED,
                Optional.of(Objects.requireNonNull(responderId, "responderId")),
                Set.of());
    }

    public static IncidentQuery administratorAll() {
        return new IncidentQuery(IncidentQueryScope.ADMINISTRATOR_ALL, Optional.empty(), Set.of());
    }

    private static void validateScope(
            IncidentQueryScope scope,
            Optional<AccountId> accountId,
            Set<IncidentCategory> categories) {
        switch (scope) {
        case REPORTER_OWNED, RESPONDER_ASSIGNED -> {
            if (accountId.isEmpty() || !categories.isEmpty()) {
                throw new IllegalArgumentException(scope + " requires only an account identifier");
            }
        }
        case RESPONDER_ELIGIBLE_UNASSIGNED -> {
            if (accountId.isPresent()) {
                throw new IllegalArgumentException(scope + " accepts categories only");
            }
        }
        case ADMINISTRATOR_ALL -> {
            if (accountId.isPresent() || !categories.isEmpty()) {
                throw new IllegalArgumentException(scope + " does not accept filters");
            }
        }
        }
    }
}

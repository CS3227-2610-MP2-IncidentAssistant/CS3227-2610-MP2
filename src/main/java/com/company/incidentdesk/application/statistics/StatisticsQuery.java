package com.company.incidentdesk.application.statistics;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.IncidentCategory;

/**
 * Role-independent statistics filter: category, privacy-safe reporter, and reporting period.
 *
 * <p>{@code reporterId} is matched only against non-anonymous incidents; an anonymous incident
 * never matches a reporter filter, so a query can never be used to correlate an anonymous report
 * with a reporter identity.
 */
public record StatisticsQuery(
        Set<IncidentCategory> categories,
        Optional<AccountId> reporterId,
        Optional<Instant> periodFrom,
        Optional<Instant> periodThrough) {
    public StatisticsQuery {
        categories = Set.copyOf(Objects.requireNonNull(categories, "categories"));
        Objects.requireNonNull(reporterId, "reporterId");
        Objects.requireNonNull(periodFrom, "periodFrom");
        Objects.requireNonNull(periodThrough, "periodThrough");
        if (periodFrom.isPresent() && periodThrough.isPresent()
                && periodFrom.orElseThrow().isAfter(periodThrough.orElseThrow())) {
            throw new IllegalArgumentException("periodFrom must not be after periodThrough");
        }
    }

    public static StatisticsQuery defaults() {
        return new StatisticsQuery(Set.of(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}

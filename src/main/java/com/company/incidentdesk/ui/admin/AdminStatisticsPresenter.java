package com.company.incidentdesk.ui.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.statistics.StatisticsGateway;
import com.company.incidentdesk.application.statistics.StatisticsQuery;
import com.company.incidentdesk.application.statistics.StatisticsResult;
import com.company.incidentdesk.domain.incident.IncidentCategory;

/** UI-thread state holder for the administrator operational-statistics dashboard. */
public final class AdminStatisticsPresenter {
    public enum State { LOADING, READY, UNAVAILABLE, STORAGE_ERROR }

    private static final StatisticsResult EMPTY_RESULT = new StatisticsResult(Optional.empty(), List.of(), List.of());

    private final StatisticsGateway statistics;
    private Optional<IncidentCategory> category = Optional.empty();
    private Optional<LocalDate> periodFrom = Optional.empty();
    private Optional<LocalDate> periodThrough = Optional.empty();
    private StatisticsResult result = EMPTY_RESULT;
    private State state = State.LOADING;

    public AdminStatisticsPresenter(StatisticsGateway statistics) {
        this.statistics = Objects.requireNonNull(statistics, "statistics");
    }

    public void load() {
        state = State.LOADING;
        query();
    }

    public void setCategory(Optional<IncidentCategory> category) {
        this.category = Objects.requireNonNull(category, "category");
        query();
    }

    public void setPeriodFrom(Optional<LocalDate> periodFrom) {
        this.periodFrom = Objects.requireNonNull(periodFrom, "periodFrom");
        query();
    }

    public void setPeriodThrough(Optional<LocalDate> periodThrough) {
        this.periodThrough = Objects.requireNonNull(periodThrough, "periodThrough");
        query();
    }

    public State state() {
        return state;
    }

    public Optional<IncidentCategory> category() {
        return category;
    }

    public Optional<LocalDate> periodFrom() {
        return periodFrom;
    }

    public Optional<LocalDate> periodThrough() {
        return periodThrough;
    }

    public StatisticsResult result() {
        return result;
    }

    private void query() {
        ZoneId zone = ZoneId.systemDefault();
        Set<IncidentCategory> categories = category.map(Set::of).orElseGet(Set::of);
        Optional<Instant> from = periodFrom.map(date -> date.atStartOfDay(zone).toInstant());
        Optional<Instant> through = periodThrough
                .map(date -> date.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1));
        if (isInvertedPeriod(from, through)) {
            state = State.UNAVAILABLE;
            result = EMPTY_RESULT;
            return;
        }
        StatisticsQuery statisticsQuery = new StatisticsQuery(categories, Optional.empty(), from, through);
        ApplicationResult<StatisticsResult> outcome = statistics.companyStatistics(statisticsQuery, true);
        if (!outcome.isSuccess()) {
            fail(outcome.error().orElseThrow());
            return;
        }
        result = outcome.value().orElseThrow();
        state = State.READY;
    }

    private void fail(ApplicationError error) {
        state = switch (error.code()) {
        case PERSISTENCE_FAILURE, CORRUPT_DATA -> State.STORAGE_ERROR;
        case VALIDATION, ACCESS_DENIED, RESOURCE_UNAVAILABLE, INVALID_STATE -> State.UNAVAILABLE;
        };
        if (state == State.UNAVAILABLE) {
            result = EMPTY_RESULT;
        }
    }

    private static boolean isInvertedPeriod(Optional<Instant> from, Optional<Instant> through) {
        return from.isPresent() && through.isPresent() && from.orElseThrow().isAfter(through.orElseThrow());
    }
}

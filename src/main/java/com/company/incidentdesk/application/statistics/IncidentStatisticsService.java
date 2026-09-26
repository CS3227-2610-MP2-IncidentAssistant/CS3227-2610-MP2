package com.company.incidentdesk.application.statistics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.company.incidentdesk.application.audit.AuditActorLabelResolver;
import com.company.incidentdesk.application.authorization.StatisticsAuthorizationPolicy;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.statistics.IncidentStatisticsCalculator;
import com.company.incidentdesk.domain.statistics.ResponderStatistics;
import com.company.incidentdesk.domain.statistics.StatisticsSummary;
import com.company.incidentdesk.persistence.AccountRepository;
import com.company.incidentdesk.persistence.AssignmentState;
import com.company.incidentdesk.persistence.IncidentQuery;
import com.company.incidentdesk.persistence.IncidentRepository;
import com.company.incidentdesk.persistence.IncidentSearchCriteria;
import com.company.incidentdesk.persistence.IncidentSort;
import com.company.incidentdesk.persistence.IncidentSortField;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.SortDirection;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** Shared, authorization-checked operational-statistics queries for responders and administrators. */
public final class IncidentStatisticsService implements StatisticsGateway {
    private final IncidentRepository incidentRepository;
    private final AccountRepository accountRepository;
    private final StatisticsAuthorizationPolicy authorizationPolicy;

    public IncidentStatisticsService(
            IncidentRepository incidentRepository,
            AccountRepository accountRepository,
            StatisticsAuthorizationPolicy authorizationPolicy) {
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy, "authorizationPolicy");
    }

    @Override
    public ApplicationResult<StatisticsResult> responderPerformance(AccountId responderId, StatisticsQuery query) {
        Objects.requireNonNull(responderId, "responderId");
        Objects.requireNonNull(query, "query");
        if (!authorizationPolicy.authorizeResponderPerformance(responderId).isAllowed()) {
            return unavailable();
        }
        try {
            List<Incident> scoped = fetchInScope(query);
            ResponderStatistics statistics = IncidentStatisticsCalculator
                    .byResponder(scoped, query.periodFrom(), query.periodThrough()).stream()
                    .filter(entry -> entry.responderId().equals(responderId))
                    .findFirst()
                    .orElseGet(() -> ResponderStatistics.empty(responderId));
            ResponderStatisticsView view = new ResponderStatisticsView(
                    responderId, displayName(responderId), statistics);
            return ApplicationResult.success(new StatisticsResult(
                    Optional.empty(), List.of(view), buildSeries(List.of(view))));
        } catch (RepositoryException exception) {
            return storageFailure(exception);
        }
    }

    @Override
    public ApplicationResult<StatisticsResult> companyStatistics(StatisticsQuery query, boolean groupByResponder) {
        Objects.requireNonNull(query, "query");
        if (!authorizationPolicy.authorizeCompanyStatistics().isAllowed()) {
            return unavailable();
        }
        try {
            List<Incident> scoped = fetchInScope(query);
            StatisticsSummary summary = IncidentStatisticsCalculator.summarize(
                    scoped, query.periodFrom(), query.periodThrough());
            List<ResponderStatisticsView> responders = groupByResponder
                    ? IncidentStatisticsCalculator.byResponder(scoped, query.periodFrom(), query.periodThrough())
                            .stream()
                            .map(statistics -> new ResponderStatisticsView(
                                    statistics.responderId(), displayName(statistics.responderId()), statistics))
                            .toList()
                    : List.of();
            return ApplicationResult.success(new StatisticsResult(
                    Optional.of(summary), responders, buildSeries(responders)));
        } catch (RepositoryException exception) {
            return storageFailure(exception);
        }
    }

    private List<Incident> fetchInScope(StatisticsQuery query) {
        IncidentSearchCriteria criteria = new IncidentSearchCriteria(
                "",
                query.categories(),
                Set.of(),
                AssignmentState.ANY,
                query.reporterId(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                new IncidentSort(IncidentSortField.CREATED_AT, SortDirection.DESCENDING));
        return incidentRepository.find(IncidentQuery.administratorAll(), criteria);
    }

    private String displayName(AccountId responderId) {
        return accountRepository.findById(responderId)
                .map(account -> account.isDeleted() ? AuditActorLabelResolver.DELETED_ACCOUNT_LABEL
                        : account.loginName())
                .orElse(AuditActorLabelResolver.DELETED_ACCOUNT_LABEL);
    }

    private static List<MetricSeries> buildSeries(List<ResponderStatisticsView> responders) {
        if (responders.isEmpty()) {
            return List.of();
        }
        List<MetricSeriesPoint> resolved = new ArrayList<>();
        List<MetricSeriesPoint> progress = new ArrayList<>();
        List<MetricSeriesPoint> reopenRate = new ArrayList<>();
        for (ResponderStatisticsView view : responders) {
            ResponderStatistics statistics = view.statistics();
            resolved.add(new MetricSeriesPoint(
                    view.displayName(), statistics.resolvedCycleCount(), statistics.resolvedCycleCount()));
            progress.add(new MetricSeriesPoint(
                    view.displayName(), seconds(statistics.averageTimeInProgress()), statistics.resolvedCycleCount()));
            reopenRate.add(new MetricSeriesPoint(
                    view.displayName(), statistics.reopenRate().orElse(0.0), statistics.resolvedCycleCount()));
        }
        return List.of(
                new MetricSeries("resolved_incidents", "Incidents resolved", MetricUnit.COUNT, resolved),
                new MetricSeries("average_time_in_progress", "Average time in progress", MetricUnit.SECONDS, progress),
                new MetricSeries("reopen_rate", "Reopen rate", MetricUnit.RATIO, reopenRate));
    }

    private static double seconds(Optional<Duration> duration) {
        return duration.map(Duration::toSeconds).orElse(0L).doubleValue();
    }

    private static <T> ApplicationResult<T> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }

    private static <T> ApplicationResult<T> storageFailure(RepositoryException exception) {
        ApplicationErrorCode code = exception.code() == StorageFailureCode.CORRUPT_DATA
                ? ApplicationErrorCode.CORRUPT_DATA
                : ApplicationErrorCode.PERSISTENCE_FAILURE;
        return ApplicationResult.failure(ApplicationError.of(code));
    }
}

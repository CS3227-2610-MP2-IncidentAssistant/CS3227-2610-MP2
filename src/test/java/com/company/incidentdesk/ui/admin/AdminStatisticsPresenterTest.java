package com.company.incidentdesk.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.statistics.StatisticsGateway;
import com.company.incidentdesk.application.statistics.StatisticsQuery;
import com.company.incidentdesk.application.statistics.StatisticsResult;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.slo.SloEvaluation;
import com.company.incidentdesk.domain.statistics.StatisticsSummary;

class AdminStatisticsPresenterTest {
    @Test
    void startsLoadingAndReachesReadyWithTheGatewayResult() {
        FakeGateway gateway = new FakeGateway();
        gateway.result = new StatisticsResult(
                Optional.of(new StatisticsSummary(SloEvaluation.empty(), 0)), List.of(), List.of());
        AdminStatisticsPresenter presenter = new AdminStatisticsPresenter(gateway);

        assertEquals(AdminStatisticsPresenter.State.LOADING, presenter.state());
        presenter.load();

        assertEquals(AdminStatisticsPresenter.State.READY, presenter.state());
        assertTrue(presenter.result().companySummary().isPresent());
    }

    @Test
    void changingFiltersReRunsTheQueryWithTheSelectedScope() {
        FakeGateway gateway = new FakeGateway();
        gateway.result = new StatisticsResult(Optional.empty(), List.of(), List.of());
        AdminStatisticsPresenter presenter = new AdminStatisticsPresenter(gateway);
        presenter.load();

        presenter.setCategory(Optional.of(IncidentCategory.FACILITIES));
        presenter.setPeriodFrom(Optional.of(LocalDate.of(2026, 1, 1)));
        presenter.setPeriodThrough(Optional.of(LocalDate.of(2026, 1, 31)));

        assertEquals(4, gateway.queries.size());
        assertEquals(Set.of(IncidentCategory.FACILITIES),gateway.queries.getLast().categories());
        assertTrue(gateway.queries.getLast().periodFrom().isPresent());
        assertTrue(gateway.queries.getLast().periodThrough().isPresent());
    }

    @Test
    void anInvertedPeriodIsRejectedWithoutQueryingTheGateway() {
        FakeGateway gateway = new FakeGateway();
        gateway.result = new StatisticsResult(Optional.empty(), List.of(), List.of());
        AdminStatisticsPresenter presenter = new AdminStatisticsPresenter(gateway);
        presenter.load();
        presenter.setPeriodFrom(Optional.of(LocalDate.of(2026, 2, 1)));

        presenter.setPeriodThrough(Optional.of(LocalDate.of(2026, 1, 1)));

        assertEquals(AdminStatisticsPresenter.State.UNAVAILABLE, presenter.state());
    }

    @Test
    void mapsDeniedActorsToUnavailableWithoutPrivilegedData() {
        FakeGateway gateway = new FakeGateway();
        gateway.failure = ApplicationErrorCode.RESOURCE_UNAVAILABLE;
        AdminStatisticsPresenter presenter = new AdminStatisticsPresenter(gateway);

        presenter.load();

        assertEquals(AdminStatisticsPresenter.State.UNAVAILABLE, presenter.state());
        assertTrue(presenter.result().companySummary().isEmpty());
        assertTrue(presenter.result().responders().isEmpty());
    }

    @Test
    void reportsStorageFailureSeparatelyFromUnavailable() {
        FakeGateway gateway = new FakeGateway();
        gateway.failure = ApplicationErrorCode.PERSISTENCE_FAILURE;
        AdminStatisticsPresenter presenter = new AdminStatisticsPresenter(gateway);

        presenter.load();

        assertEquals(AdminStatisticsPresenter.State.STORAGE_ERROR, presenter.state());
    }

    private static final class FakeGateway implements StatisticsGateway {
        private final List<StatisticsQuery> queries = new ArrayList<>();
        private StatisticsResult result;
        private ApplicationErrorCode failure;

        @Override
        public ApplicationResult<StatisticsResult> responderPerformance(AccountId responderId, StatisticsQuery query) {
            throw new UnsupportedOperationException("not used by this presenter");
        }

        @Override
        public ApplicationResult<StatisticsResult> companyStatistics(StatisticsQuery query, boolean groupByResponder) {
            queries.add(query);
            if (failure != null) {
                return ApplicationResult.failure(ApplicationError.of(failure));
            }
            return ApplicationResult.success(result);
        }
    }
}

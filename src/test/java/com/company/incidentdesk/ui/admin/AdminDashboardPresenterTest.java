package com.company.incidentdesk.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.presentation.IncidentActionModel;
import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.presentation.SloSummaryModel;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentId;

class AdminDashboardPresenterTest {
    private final MutableSessions sessions = new MutableSessions();
    private final AdminDashboardPresenter presenter = new AdminDashboardPresenter(sessions);
    private final IncidentRowModel row = row();

    @Test
    void successfulRefreshPublishesAdministratorRows() {
        presenter.completeRefresh(presenter.beginRefresh(), ApplicationResult.success(List.of(row)));

        assertEquals(AdminDashboardPresenter.State.READY, presenter.state());
        assertEquals(List.of(row), presenter.rows());
    }

    @Test
    void missingOrNonAdministratorSessionIsNeutralUnavailable() {
        sessions.account = null;
        presenter.beginRefresh();
        assertUnavailable();

        sessions.account = account(Role.REPORTER);
        presenter.beginRefresh();
        assertUnavailable();
    }

    @Test
    void logoutRoleLossAndSessionReplacementClearPrivilegedRows() {
        for (Account replacement : List.of(account(Role.REPORTER), account(Role.RESPONDER))) {
            sessions.account = account(Role.ADMINISTRATOR);
            long request = presenter.beginRefresh();
            sessions.account = replacement;
            presenter.completeRefresh(request, ApplicationResult.success(List.of(row)));
            assertUnavailable();
        }

        sessions.account = account(Role.ADMINISTRATOR);
        presenter.completeRefresh(presenter.beginRefresh(), ApplicationResult.success(List.of(row)));
        sessions.authenticatedAt = sessions.authenticatedAt.plusSeconds(1);
        assertUnavailable();
    }

    @Test
    void lateResultCannotRestoreClearedData() {
        long request = presenter.beginRefresh();
        presenter.clear();
        presenter.completeRefresh(request, ApplicationResult.success(List.of(row)));
        assertUnavailable();
    }

    private void assertUnavailable() {
        assertEquals(AdminDashboardPresenter.State.UNAVAILABLE, presenter.state());
        assertTrue(presenter.rows().isEmpty());
    }

    private static IncidentRowModel row() {
        return new IncidentRowModel(new IncidentId(new UUID(1, 1)), "Printer", "IT", "Submitted",
                "Reporter", "Unassigned", "23 Sep 2026, 16:00", "23 Sep 2026, 16:00",
                SloSummaryModel.unavailable(), false, 0,
                new IncidentActionModel(false, false, false, false, false, false, false, false, false));
    }

    private static Account account(Role role) {
        return new Account(new AccountId(new UUID(0, 1)), role.name(), role,
                AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    private static final class MutableSessions implements SessionProvider {
        private Account account = account(Role.ADMINISTRATOR);
        private Instant authenticatedAt = Instant.parse("2026-09-23T08:00:00Z");

        @Override public Optional<AuthenticatedSession> currentSession() {
            return Optional.ofNullable(account).map(value -> new AuthenticatedSession(value.id(), authenticatedAt));
        }

        @Override public Optional<Account> currentAccount() {
            return Optional.ofNullable(account);
        }
    }
}

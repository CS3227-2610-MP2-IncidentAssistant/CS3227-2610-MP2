package com.company.incidentdesk.ui.responder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.presentation.IncidentActionModel;
import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.presentation.ResponderDashboardModel;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

class ResponderDashboardPresenterTest {
    private final MutableSessions sessions = new MutableSessions();
    private final ResponderDashboardPresenter presenter = new ResponderDashboardPresenter(sessions);
    private final IncidentRowModel row = row();
    private final ResponderDashboardModel populated = new ResponderDashboardModel(List.of(row), List.of());

    @Test
    void loadingClearsOldRowsButRefreshPreservesValidSelection() {
        complete(populated);
        presenter.select(row.id());

        long request = presenter.beginRefresh();
        assertEquals(ResponderDashboardPresenter.State.LOADING, presenter.state());
        assertEquals(ResponderDashboardModel.empty(), presenter.model());
        assertTrue(presenter.selectedIncident().isEmpty());
        presenter.completeRefresh(request, ApplicationResult.success(populated));

        assertEquals(Optional.of(row.id()), presenter.selectedIncident());
    }

    @Test
    void successfulEmptyRefreshClearsUnavailableSelection() {
        complete(populated);
        presenter.select(row.id());
        complete(ResponderDashboardModel.empty());

        assertEquals(ResponderDashboardPresenter.State.READY, presenter.state());
        assertTrue(presenter.selectedIncident().isEmpty());
        assertEquals(ResponderDashboardModel.empty(), presenter.model());
    }

    @Test
    void failedRefreshClearsRowsAndSelectionAndCanBeRetried() {
        complete(populated);
        presenter.select(row.id());
        long request = presenter.beginRefresh();
        presenter.completeRefresh(request, ApplicationResult.failure(
                ApplicationError.of(ApplicationErrorCode.PERSISTENCE_FAILURE)));

        assertUnavailable();
        complete(populated);
        assertEquals(ResponderDashboardPresenter.State.READY, presenter.state());
    }

    @Test
    void sessionAndPermissionChangesDiscardInFlightResults() {
        for (Account changed : List.of(account(Role.REPORTER, ResponderAccess.NONE),
                account(Role.ADMINISTRATOR, ResponderAccess.NONE), account(Role.RESPONDER, ResponderAccess.NONE))) {
            sessions.account = account(Role.RESPONDER, ResponderAccess.to(Set.of(IncidentCategory.IT)));
            long request = presenter.beginRefresh();
            sessions.account = changed;
            presenter.completeRefresh(request, ApplicationResult.success(populated));
            assertUnavailable();
        }
    }

    @Test
    void signOutClearsSelectionBeforeNavigationAndDeniesAnotherLoad() {
        complete(populated);
        presenter.select(row.id());
        sessions.account = null;

        assertTrue(presenter.selectedIncident().isEmpty());
        presenter.beginRefresh();
        assertUnavailable();
    }

    @Test
    void categoryRevocationClearsRowsOnNextSelectionOperation() {
        complete(populated);
        sessions.account = account(Role.RESPONDER, ResponderAccess.NONE);
        presenter.select(row.id());
        assertUnavailable();
    }

    @Test
    void signedOutAndNonResponderSessionsCannotStartLoading() {
        sessions.account = null;
        presenter.beginRefresh();
        assertUnavailable();
        for (Role role : List.of(Role.REPORTER, Role.ADMINISTRATOR)) {
            sessions.account = account(role, ResponderAccess.NONE);
            presenter.beginRefresh();
            assertUnavailable();
        }
    }

    @Test
    void oldReadCannotReplaceNewerRefreshOrRestoreDetachedPage() {
        long oldRequest = presenter.beginRefresh();
        complete(ResponderDashboardModel.empty());
        presenter.completeRefresh(oldRequest, ApplicationResult.success(populated));
        assertEquals(ResponderDashboardModel.empty(), presenter.model());
        long detachedRequest = presenter.beginRefresh();
        presenter.clear();
        presenter.completeRefresh(detachedRequest, ApplicationResult.success(populated));
        assertUnavailable();
    }

    @Test
    void newLoginOfSameAccountInvalidatesEarlierRead() {
        long request = presenter.beginRefresh();
        sessions.authenticatedAt = sessions.authenticatedAt.plusSeconds(1);
        presenter.completeRefresh(request, ApplicationResult.success(populated));
        assertUnavailable();
    }

    @Test
    void unreadableAccountStateFailsClosed() {
        complete(populated);
        sessions.fail = true;
        presenter.beginRefresh();
        assertUnavailable();
    }

    private void complete(ResponderDashboardModel model) {
        presenter.completeRefresh(presenter.beginRefresh(), ApplicationResult.success(model));
    }

    private void assertUnavailable() {
        assertEquals(ResponderDashboardPresenter.State.UNAVAILABLE, presenter.state());
        assertEquals(ResponderDashboardModel.empty(), presenter.model());
        assertTrue(presenter.selectedIncident().isEmpty());
    }

    static IncidentRowModel row() {
        return new IncidentRowModel(new IncidentId(new UUID(1, 1)), "Printer", "IT", "Submitted",
                "Anonymous reporter", "Unassigned", "23 Sep 2026, 16:00", true, 0,
                new IncidentActionModel(false, false, true, false, false, false, false, true, true));
    }

    private static Account account(Role role, ResponderAccess access) {
        return new Account(new AccountId(new UUID(0, 1)), "Responder", role, AccountStatus.ENABLED, access);
    }

    static final class MutableSessions implements SessionProvider {
        volatile Account account = account(Role.RESPONDER, ResponderAccess.to(Set.of(IncidentCategory.IT)));
        Instant authenticatedAt = Instant.parse("2026-09-23T08:00:00Z");
        boolean fail;

        @Override public Optional<AuthenticatedSession> currentSession() {
            return currentAccount().map(value -> new AuthenticatedSession(value.id(), authenticatedAt));
        }

        @Override public Optional<Account> currentAccount() {
            if (fail) {
                throw new RepositoryException(StorageFailureCode.STORAGE_UNAVAILABLE, "Private path");
            }
            return Optional.ofNullable(account);
        }
    }
}

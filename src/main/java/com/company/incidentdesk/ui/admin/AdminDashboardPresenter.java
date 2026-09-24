package com.company.incidentdesk.ui.admin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.persistence.RepositoryException;

/** UI-thread state machine that prevents stale privileged administrator data. */
public final class AdminDashboardPresenter {
    public enum State { LOADING, READY, UNAVAILABLE }

    private final SessionProvider sessions;
    private Optional<Context> context = Optional.empty();
    private List<IncidentRowModel> rows = List.of();
    private State state = State.UNAVAILABLE;
    private long revision;

    public AdminDashboardPresenter(SessionProvider sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public long beginRefresh() {
        context = currentContext();
        rows = List.of();
        state = context.isPresent() ? State.LOADING : State.UNAVAILABLE;
        return ++revision;
    }

    public void completeRefresh(long request, ApplicationResult<List<IncidentRowModel>> result) {
        Objects.requireNonNull(result, "result");
        if (request != revision) {
            return;
        }
        if (!isContextCurrent() || !result.isSuccess()) {
            clear();
            return;
        }
        rows = List.copyOf(result.value().orElseThrow());
        state = State.READY;
    }

    public void clear() {
        revision++;
        context = Optional.empty();
        rows = List.of();
        state = State.UNAVAILABLE;
    }

    public List<IncidentRowModel> rows() {
        if (!isContextCurrent()) {
            clear();
        }
        return rows;
    }

    public State state() {
        if (state != State.UNAVAILABLE && !isContextCurrent()) {
            clear();
        }
        return state;
    }

    private boolean isContextCurrent() {
        return context.isPresent() && context.equals(currentContext());
    }

    private Optional<Context> currentContext() {
        try {
            Optional<AuthenticatedSession> session = sessions.currentSession();
            Optional<Account> account = sessions.currentAccount()
                    .filter(Account::isEnabled)
                    .filter(value -> value.role() == Role.ADMINISTRATOR);
            if (session.isEmpty() || account.isEmpty()
                    || !session.orElseThrow().accountId().equals(account.orElseThrow().id())) {
                return Optional.empty();
            }
            return Optional.of(new Context(session.orElseThrow(), account.orElseThrow()));
        } catch (RepositoryException exception) {
            return Optional.empty();
        }
    }

    private record Context(AuthenticatedSession session, Account account) { }
}

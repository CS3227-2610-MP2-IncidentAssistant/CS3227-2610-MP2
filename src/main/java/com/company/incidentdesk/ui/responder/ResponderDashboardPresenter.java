package com.company.incidentdesk.ui.responder;

import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.application.presentation.ResponderDashboardModel;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.persistence.RepositoryException;

/** UI-thread state machine; late reads cannot restore data from an invalidated session. */
public final class ResponderDashboardPresenter {
    public enum State { LOADING, READY, UNAVAILABLE }

    private final SessionProvider sessions;
    private Optional<Context> context = Optional.empty();
    private ResponderDashboardModel model = ResponderDashboardModel.empty();
    private Optional<IncidentId> selection = Optional.empty();
    private State state = State.UNAVAILABLE;
    private long revision;

    public ResponderDashboardPresenter(SessionProvider sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public long beginRefresh() {
        Optional<Context> latest = currentContext();
        if (!context.equals(latest)) {
            selection = Optional.empty();
        }
        context = latest;
        model = ResponderDashboardModel.empty();
        state = latest.isPresent() ? State.LOADING : State.UNAVAILABLE;
        return ++revision;
    }

    public void completeRefresh(long request, ApplicationResult<ResponderDashboardModel> result) {
        if (request != revision) {
            return;
        }
        if (!isContextCurrent() || !result.isSuccess()) {
            clear();
            return;
        }
        model = result.value().orElseThrow();
        selection = selection.filter(this::contains);
        state = State.READY;
    }

    public void select(IncidentId id) {
        if (!isContextCurrent()) {
            clear();
            return;
        }
        selection = Optional.ofNullable(id).filter(this::contains);
    }

    public Optional<IncidentId> selectedIncident() {
        if (!isContextCurrent()) {
            clear();
        }
        // Keep a refresh candidate internally, but never offer it for navigation while loading.
        return state == State.READY ? selection : Optional.empty();
    }

    public void clear() {
        revision++;
        context = Optional.empty();
        model = ResponderDashboardModel.empty();
        selection = Optional.empty();
        state = State.UNAVAILABLE;
    }

    public ResponderDashboardModel model() { return model; }
    public State state() { return state; }

    private boolean contains(IncidentId id) {
        return model.eligible().stream().anyMatch(row -> row.id().equals(id))
                || model.assigned().stream().anyMatch(row -> row.id().equals(id));
    }

    private boolean isContextCurrent() {
        return context.isPresent() && context.equals(currentContext());
    }

    private Optional<Context> currentContext() {
        try {
            Optional<AuthenticatedSession> session = sessions.currentSession();
            Optional<Account> account = sessions.currentAccount()
                    .filter(Account::isEnabled).filter(value -> value.role() == Role.RESPONDER);
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

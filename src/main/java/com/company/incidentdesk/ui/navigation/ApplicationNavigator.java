package com.company.incidentdesk.ui.navigation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.company.incidentdesk.application.notification.NotificationInbox;
import com.company.incidentdesk.application.account.AccountRegistrar;
import com.company.incidentdesk.application.session.SessionService;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.ui.shared.components.ComponentShowcasePage;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;

/** Owns authentication boundaries and navigation for the single active session. */
public final class ApplicationNavigator implements AutoCloseable {
    private final Scene scene;
    private final SessionService sessions;
    private final NotificationInbox notifications;
    private final ViewFactory views;
    private final AccountRegistrar registrations;
    private final Map<ApplicationRoute, Node> retainedViews = new EnumMap<>(ApplicationRoute.class);
    private AuthenticatedShell shell;
    private Account shellAccount;
    private AuthenticatedSession shellSession;

    public ApplicationNavigator(
            Scene scene,
            SessionService sessions,
            NotificationInbox notifications,
            ViewFactory views,
            AccountRegistrar registrations) {
        this.scene = Objects.requireNonNull(scene, "scene");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.views = Objects.requireNonNull(views, "views");
        this.registrations = Objects.requireNonNull(registrations, "registrations");
    }

    /** Shows the current account's shell, or authentication when no valid session exists. */
    public void start() {
        sessions.currentAccount().ifPresentOrElse(this::showDashboard, this::showAuthentication);
    }

    public void navigate(ApplicationRoute route) {
        Objects.requireNonNull(route, "route");
        Account account = sessions.currentAccount().orElse(null);
        AuthenticatedSession session = sessions.currentSession().orElse(null);
        if (account == null || session == null || !session.accountId().equals(account.id())) {
            if (route.isAdministratorOnly()) {
                showUnavailable();
            } else {
                showAuthentication();
            }
            return;
        }
        ensureShell(account, session);
        if (!route.isAvailableTo(account.role())) {
            shell.showUnavailable(unavailableView());
            return;
        }
        Node destination = retainedViews.computeIfAbsent(
                route,
                selected -> views.createView(account, selected, this::openIncident));
        shell.show(route, destination);
    }

    public void openIncident(IncidentId incidentId) {
        Account account = sessions.currentAccount().orElse(null);
        if (account == null) {
            showAuthentication();
            return;
        }
        AuthenticatedSession session = sessions.currentSession().orElse(null);
        if (session == null || !session.accountId().equals(account.id())) {
            showUnavailable();
            return;
        }
        ensureShell(account, session);
        shell.showDetail(views.createIncidentDetail(account, Objects.requireNonNull(incidentId, "incidentId"),
                () -> navigate(ApplicationRoute.DASHBOARD)));
    }

    public void logout() {
        sessions.logout();
        showAuthentication();
    }

    private void showDashboard(Account account) {
        navigate(ApplicationRoute.DASHBOARD);
    }

    private void showAuthentication() {
        clearShell();
        scene.setRoot(new AuthenticationPage(sessions, registrations, this::start, this::showSampleUi));
    }

    private void showSampleUi() {
        clearShell();
        scene.setRoot(new ComponentShowcasePage(this::showAuthentication));
    }

    private void ensureShell(Account account, AuthenticatedSession session) {
        if (shell != null && account.equals(shellAccount) && session.equals(shellSession)) {
            return;
        }
        clearShell();
        shellAccount = account;
        shellSession = session;
        shell = new AuthenticatedShell(account, notifications, this::navigate, this::logout);
        scene.setRoot(shell);
    }

    private void clearShell() {
        if (shell != null) {
            shell.close();
        }
        shell = null;
        shellAccount = null;
        shellSession = null;
        retainedViews.clear();
    }

    private void showUnavailable() {
        clearShell();
        scene.setRoot(unavailableView());
    }

    private VBox unavailableView() {
        return UiComponents.feedback(
                "Administrator area unavailable",
                "Sign in with an administrator account to continue.",
                FeedbackType.EMPTY);
    }

    @Override
    public void close() {
        clearShell();
    }
}

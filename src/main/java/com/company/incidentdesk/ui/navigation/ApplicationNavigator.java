package com.company.incidentdesk.ui.navigation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.company.incidentdesk.application.notification.NotificationInbox;
import com.company.incidentdesk.application.account.AccountRegistrar;
import com.company.incidentdesk.application.session.SessionService;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.ui.shared.components.ComponentShowcasePage;

import javafx.scene.Node;
import javafx.scene.Scene;

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
        Account account = sessions.currentAccount().orElse(null);
        if (account == null) {
            showAuthentication();
            return;
        }
        ensureShell(account);
        Node destination = retainedViews.computeIfAbsent(
                Objects.requireNonNull(route, "route"),
                ignored -> views.createDashboard(account, this::openIncident));
        shell.show(destination);
    }

    public void openIncident(IncidentId incidentId) {
        Account account = sessions.currentAccount().orElse(null);
        if (account == null) {
            showAuthentication();
            return;
        }
        ensureShell(account);
        shell.show(views.createIncidentDetail(account, Objects.requireNonNull(incidentId, "incidentId"),
                () -> navigate(ApplicationRoute.DASHBOARD)));
    }

    public void logout() {
        sessions.logout();
        showAuthentication();
    }

    private void showDashboard(Account account) {
        ensureShell(account);
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

    private void ensureShell(Account account) {
        if (shell != null && account.equals(shellAccount)) {
            return;
        }
        clearShell();
        shellAccount = account;
        shell = new AuthenticatedShell(account, notifications, this::navigate, this::logout);
        scene.setRoot(shell);
    }

    private void clearShell() {
        if (shell != null) {
            shell.close();
        }
        shell = null;
        shellAccount = null;
        retainedViews.clear();
    }

    @Override
    public void close() {
        clearShell();
    }
}

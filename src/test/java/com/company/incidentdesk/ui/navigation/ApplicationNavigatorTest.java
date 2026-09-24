package com.company.incidentdesk.ui.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.notification.NotificationInbox;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.AuthenticationResult;
import com.company.incidentdesk.application.session.SessionService;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.ui.shared.components.ComponentShowcasePage;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

class ApplicationNavigatorTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    void everyRoleUsesAuthenticatedShellWithoutRoleSelection() throws Exception {
        for (Role role : Role.values()) {
            onFx(() -> {
                MutableSessions sessions = new MutableSessions(account(role));
                Scene scene = new Scene(new VBox());
                ApplicationNavigator navigator = new ApplicationNavigator(
                        scene, sessions, new NotificationInbox(), new RecordingViews(), (name, password, selectedRole) -> null);
                navigator.start();
                assertInstanceOf(AuthenticatedShell.class, scene.getRoot());
                assertEquals(role.name(), ((Label) scene.lookup("#dashboard-view")).getText());
                navigator.close();
                return null;
            });
        }
    }

    @Test
    void logoutClearsSessionAndReturnsToAuthentication() throws Exception {
        onFx(() -> {
            MutableSessions sessions = new MutableSessions(account(Role.REPORTER));
            Scene scene = new Scene(new VBox());
            ApplicationNavigator navigator = new ApplicationNavigator(
                    scene, sessions, new NotificationInbox(), new RecordingViews(), (name, password, role) -> null);
            navigator.start();
            navigator.logout();
            assertTrue(sessions.logoutCalled);
            assertInstanceOf(AuthenticationPage.class, scene.getRoot());
            navigator.close();
            return null;
        });
    }

    @Test
    void authenticatedControlsAreContainedInSidebar() throws Exception {
        onFx(() -> {
            MutableSessions sessions = new MutableSessions(account(Role.ADMINISTRATOR));
            Scene scene = new Scene(new VBox());
            ApplicationNavigator navigator = new ApplicationNavigator(
                    scene, sessions, new NotificationInbox(), new RecordingViews(), (name, password, role) -> null);
            navigator.start();

            AuthenticatedShell shell = assertInstanceOf(AuthenticatedShell.class, scene.getRoot());
            assertEquals(null, shell.getTop());
            assertEquals("A", ((Label) scene.lookup("#current-user-avatar")).getText());
            Button updatePassword = (Button) scene.lookup("#update-password-navigation");
            VBox accountControls = (VBox) updatePassword.getParent();
            assertEquals("current-user-account", accountControls.getChildren().get(0).getId());
            assertSame(updatePassword, accountControls.getChildren().get(1));
            assertSame(scene.lookup("#logout-navigation"), accountControls.getChildren().get(2));
            assertEquals(8, accountControls.getSpacing());
            assertTrue(scene.lookup("#dashboard-navigation").getStyleClass().contains("active-navigation"));
            ((Button) scene.lookup("#logout-navigation")).fire();
            assertTrue(sessions.logoutCalled);
            navigator.close();
            return null;
        });
    }

    @Test
    void selectedRouteReceivesActiveNavigationStyle() throws Exception {
        onFx(() -> {
            Scene scene = new Scene(new VBox());
            ApplicationNavigator navigator = new ApplicationNavigator(
                    scene, new MutableSessions(account(Role.ADMINISTRATOR)), new NotificationInbox(),
                    new RecordingViews(), (name, password, role) -> null);
            navigator.start();

            Node dashboard = scene.lookup("#dashboard-navigation");
            Node accounts = scene.lookup("#admin-accounts-navigation");
            navigator.navigate(ApplicationRoute.ADMIN_ACCOUNTS);

            assertTrue(!dashboard.getStyleClass().contains("active-navigation"));
            assertTrue(accounts.getStyleClass().contains("active-navigation"));
            navigator.close();
            return null;
        });
    }

    @Test
    void administratorDestinationsAreVisibleOnlyToAdministrators() throws Exception {
        for (Role role : Role.values()) {
            onFx(() -> {
                Scene scene = new Scene(new VBox());
                ApplicationNavigator navigator = new ApplicationNavigator(
                        scene, new MutableSessions(account(role)), new NotificationInbox(),
                        new RecordingViews(), (name, password, selectedRole) -> null);
                navigator.start();
                boolean shouldBeVisible = role == Role.ADMINISTRATOR;
                assertEquals(shouldBeVisible, scene.lookup("#admin-accounts-navigation") != null);
                assertEquals(shouldBeVisible, scene.lookup("#admin-slo-navigation") != null);
                navigator.close();
                return null;
            });
        }
    }

    @Test
    void directAdministratorNavigationFailsClosedForMissingAndReporterSessions() throws Exception {
        onFx(() -> {
            Scene missingScene = new Scene(new VBox());
            ApplicationNavigator missingNavigator = new ApplicationNavigator(
                    missingScene, new MutableSessions(null), new NotificationInbox(),
                    new RecordingViews(), (name, password, role) -> null);
            missingNavigator.navigate(ApplicationRoute.ADMIN_ACCOUNTS);
            assertInstanceOf(AuthenticationPage.class, missingScene.getRoot());

            Scene reporterScene = new Scene(new VBox());
            ApplicationNavigator reporterNavigator = new ApplicationNavigator(
                    reporterScene, new MutableSessions(account(Role.REPORTER)), new NotificationInbox(),
                    new RecordingViews(), (name, password, role) -> null);
            reporterNavigator.start();
            reporterNavigator.navigate(ApplicationRoute.ADMIN_ACCOUNTS);
            assertInstanceOf(AuthenticatedShell.class, reporterScene.getRoot());
            assertTrue(reporterScene.lookup(".feedback-card") != null);
            missingNavigator.close();
            reporterNavigator.close();
            return null;
        });
    }

    @Test
    void replacementSessionRebuildsShellAndDropsRetainedViews() throws Exception {
        onFx(() -> {
            MutableSessions sessions = new MutableSessions(account(Role.ADMINISTRATOR));
            RecordingViews views = new RecordingViews();
            Scene scene = new Scene(new VBox());
            ApplicationNavigator navigator = new ApplicationNavigator(
                    scene, sessions, new NotificationInbox(), views, (name, password, role) -> null);
            navigator.start();
            Node firstDashboard = scene.lookup("#dashboard-view");

            sessions.authenticatedAt = sessions.authenticatedAt.plusSeconds(1);
            navigator.navigate(ApplicationRoute.DASHBOARD);

            assertTrue(firstDashboard != scene.lookup("#dashboard-view"));
            assertEquals(2, views.dashboardCreations);
            navigator.close();
            return null;
        });
    }

    @Test
    void returningFromDetailReusesDashboardAndPreservesItsState() throws Exception {
        onFx(() -> {
            MutableSessions sessions = new MutableSessions(account(Role.RESPONDER));
            RecordingViews views = new RecordingViews();
            Scene scene = new Scene(new VBox());
            ApplicationNavigator navigator = new ApplicationNavigator(
                    scene, sessions, new NotificationInbox(), views, (name, password, role) -> null);
            navigator.start();
            Node dashboard = scene.lookup("#dashboard-view");
            navigator.openIncident(new IncidentId(UUID.randomUUID()));
            views.back.run();
            assertSame(dashboard, scene.lookup("#dashboard-view"));
            assertEquals(1, views.dashboardCreations);
            navigator.close();
            return null;
        });
    }

    @Test
    void missingSessionRedirectsProtectedNavigationToAuthentication() throws Exception {
        onFx(() -> {
            MutableSessions sessions = new MutableSessions(null);
            for (ApplicationRoute route : ApplicationRoute.values()) {
                Scene scene = new Scene(new VBox());
                ApplicationNavigator navigator = new ApplicationNavigator(
                        scene, sessions, new NotificationInbox(), new RecordingViews(),
                        (name, password, role) -> null);
                navigator.navigate(route);
                assertInstanceOf(AuthenticationPage.class, scene.getRoot());
                navigator.close();
            }
            return null;
        });
    }

    @Test
    void sessionLossFromDashboardAndIncidentDetailReturnsToAuthentication() throws Exception {
        onFx(() -> {
            MutableSessions sessions = new MutableSessions(account(Role.ADMINISTRATOR));
            Scene scene = new Scene(new VBox());
            ApplicationNavigator navigator = new ApplicationNavigator(
                    scene, sessions, new NotificationInbox(), new RecordingViews(),
                    (name, password, role) -> null);
            navigator.start();
            sessions.account = null;

            navigator.openIncident(new IncidentId(UUID.randomUUID()));

            assertInstanceOf(AuthenticationPage.class, scene.getRoot());
            navigator.close();
            return null;
        });
    }

    @Test
    void sampleUiCanBeOpenedFromAuthenticationAndReturnedFrom() throws Exception {
        onFx(() -> {
            Scene scene = new Scene(new VBox());
            ApplicationNavigator navigator = new ApplicationNavigator(
                    scene, new MutableSessions(null), new NotificationInbox(),
                    new RecordingViews(), (name, password, role) -> null);
            navigator.start();

            ((Button) scene.lookup("#sample-ui")).fire();
            assertInstanceOf(ComponentShowcasePage.class, scene.getRoot());

            ((Button) scene.lookup("#showcase-back")).fire();
            assertInstanceOf(AuthenticationPage.class, scene.getRoot());
            navigator.close();
            return null;
        });
    }

    private static Account account(Role role) {
        return new Account(
                new AccountId(UUID.randomUUID()),
                role.name().toLowerCase(),
                role,
                AccountStatus.ENABLED,
                ResponderAccess.NONE);
    }

    private static <T> T onFx(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static final class RecordingViews implements ViewFactory {
        private int dashboardCreations;
        private Runnable back;

        @Override
        public Node createView(Account account, ApplicationRoute route, Consumer<IncidentId> onOpenIncident) {
            dashboardCreations++;
            Label dashboard = new Label(account.role().name());
            dashboard.setId("dashboard-view");
            return dashboard;
        }

        @Override
        public Node createIncidentDetail(Account account, IncidentId incidentId, Runnable onBack) {
            back = onBack;
            Label detail = new Label(incidentId.value().toString());
            detail.setId("incident-detail");
            return detail;
        }
    }

    private static final class MutableSessions implements SessionService {
        private Account account;
        private boolean logoutCalled;
        private Instant authenticatedAt = Instant.EPOCH;

        private MutableSessions(Account account) {
            this.account = account;
        }

        @Override
        public AuthenticationResult login(String loginName, char[] password) {
            return AuthenticationResult.REJECTED;
        }

        @Override
        public void logout() {
            logoutCalled = true;
            account = null;
        }

        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return Optional.ofNullable(account).map(value -> new AuthenticatedSession(value.id(), authenticatedAt));
        }

        @Override
        public Optional<Account> currentAccount() {
            return Optional.ofNullable(account);
        }
    }
}

package com.company.incidentdesk.ui.navigation;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.company.incidentdesk.application.notification.NotificationInbox;
import com.company.incidentdesk.application.session.InMemorySessionService;
import com.company.incidentdesk.application.session.SessionService;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.persistence.memory.InMemoryAccountRepository;
import com.company.incidentdesk.ui.admin.AdminPage;
import com.company.incidentdesk.ui.reporter.ReporterPage;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.UiComponents;
import com.company.incidentdesk.ui.shared.theme.ApplicationTheme;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Test-only visual preview for the shared authenticated shell. */
public final class AuthenticatedShellPreview {
    private AuthenticatedShellPreview() {
    }

    private static void show() {
        Stage stage = new Stage();
        List<Account> previewAccounts = previewAccounts();
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        previewAccounts.forEach(accounts::create);
        Set<AccountId> previewAccountIds = previewAccounts.stream()
                .map(Account::id)
                .collect(Collectors.toUnmodifiableSet());
        SessionService sessions = new InMemorySessionService(
                accounts,
                (accountId, ignoredPassword) -> previewAccountIds.contains(accountId),
                Clock.systemUTC());
        Scene scene = new Scene(new VBox(), 1100, 720);
        ApplicationTheme.applyTo(scene);
        ApplicationNavigator navigator = new ApplicationNavigator(
                scene,
                sessions,
                new NotificationInbox(),
                new PreviewViewFactory(),
                (name, password, role) -> null);
        navigator.start();
        stage.setTitle("Incident Desk — Authenticated Shell Preview");
        stage.setScene(scene);
        stage.setOnHidden(event -> navigator.close());
        stage.show();
    }

    public static void main(String[] args) {
        Platform.startup(AuthenticatedShellPreview::show);
    }

    static List<Account> previewAccounts() {
        return List.of(
                account("10000000-0000-0000-0000-000000000001", "admin",
                        Role.ADMINISTRATOR, ResponderAccess.NONE),
                account("10000000-0000-0000-0000-000000000002", "reporter",
                        Role.REPORTER, ResponderAccess.NONE),
                account("10000000-0000-0000-0000-000000000003", "responder",
                        Role.RESPONDER, ResponderAccess.to(Set.of(IncidentCategory.values()))));
    }

    private static Account account(String id, String loginName, Role role, ResponderAccess access) {
        return new Account(
                new AccountId(UUID.fromString(id)), loginName, role, AccountStatus.ENABLED, access);
    }

    private static final class PreviewViewFactory implements ViewFactory {
        @Override
        public Node createDashboard(Account account, Consumer<IncidentId> onOpenIncident) {
            return switch (account.role()) {
            case REPORTER -> new ReporterPage();
            case RESPONDER -> UiComponents.feedback(
                    "Responder",
                    "Review eligible incidents and manage incidents assigned to you.",
                    FeedbackType.EMPTY);
            case ADMINISTRATOR -> new AdminPage();
            };
        }

        @Override
        public Node createIncidentDetail(Account account, IncidentId incidentId, Runnable onBack) {
            return UiComponents.feedback(
                    "Incident detail preview",
                    "Return to the dashboard to continue testing navigation.",
                    FeedbackType.EMPTY);
        }
    }
}

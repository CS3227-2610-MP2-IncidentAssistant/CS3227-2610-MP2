package com.company.incidentdesk.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.audit.AuditActorLabelResolver;
import com.company.incidentdesk.application.audit.AuditLogService;
import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActor;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.persistence.memory.InMemoryAccountRepository;
import com.company.incidentdesk.persistence.memory.InMemoryAuditRepository;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;

class AdminAuditLogPageTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test
    void displaysAuditEntriesInAccessibleTable() throws Exception {
        onFx(() -> {
            Account admin = account("10000000-0000-0000-0000-000000000001", "admin");
            InMemoryAccountRepository accounts = new InMemoryAccountRepository();
            accounts.create(admin);
            InMemoryAuditRepository audits = new InMemoryAuditRepository();
            audits.append(new AuditEvent(
                    new AuditEventId(UUID.fromString("20000000-0000-0000-0000-000000000001")),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    new AuditActor(admin.id(), Role.ADMINISTRATOR, AuditActorVisibility.STANDARD),
                    AuditAction.SLO_CONFIGURATION_CHANGED,
                    new AuditTarget(AuditTargetType.SLO_CONFIGURATION, "global"),
                    AuditOutcome.SUCCESS,
                    List.of(),
                    Optional.empty()));
            AuditLogService service = new AuditLogService(
                    new AccountAuthorizationPolicy(new FixedSession(admin)),
                    audits,
                    new AuditActorLabelResolver(accounts),
                    accounts);

            AdminAuditLogPage page = new AdminAuditLogPage(service);
            new Scene(page, 1100, 720);
            page.onShown();
            page.applyCss();
            page.layout();
            TableView<?> table = (TableView<?>) page.lookup("#audit-log-table");

            assertEquals("Application audit log", table.getAccessibleText());
            assertEquals(40, table.getFixedCellSize());
            assertEquals(1, table.getItems().size());
            assertEquals(4, table.getColumns().size());
            assertEquals(List.of("Time", "Event ID", "Actor", "Event"),
                    table.getColumns().stream().map(column -> column.getText()).toList());
            assertEquals(2, table.lookupAll(".badge").size());

            table.getSelectionModel().selectFirst();
            table.getOnMouseClicked().handle(doubleClick());
            Window dialog = Window.getWindows().stream()
                    .filter(Window::isShowing)
                    .filter(window -> window.getScene().lookup("#audit-event-details") != null)
                    .findFirst().orElseThrow();
            TextArea details = (TextArea) dialog.getScene().lookup("#audit-event-details");
            assertTrue(!details.isEditable());
            assertTrue(details.getText().contains("Slo configuration changed"));
            dialog.hide();

            audits.append(new AuditEvent(
                    new AuditEventId(UUID.fromString("20000000-0000-0000-0000-000000000002")),
                    Instant.parse("2026-01-02T00:00:00Z"),
                    new AuditActor(admin.id(), Role.ADMINISTRATOR, AuditActorVisibility.STANDARD),
                    AuditAction.ACCOUNT_DELETED,
                    new AuditTarget(AuditTargetType.ACCOUNT, admin.id().value().toString()),
                    AuditOutcome.SUCCESS,
                    List.of(),
                    Optional.empty()));
            page.onShown();

            TableView<?> refreshedTable = (TableView<?>) page.lookup("#audit-log-table");
            assertEquals(2, refreshedTable.getItems().size());
            return null;
        });
    }

    private static MouseEvent doubleClick() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
                false, false, false, false, true, false, false, true, false, false, null);
    }

    private static Account account(String id, String loginName) {
        return new Account(
                new AccountId(UUID.fromString(id)), loginName, Role.ADMINISTRATOR,
                AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    private static <T> T onFx(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private record FixedSession(Account account) implements SessionProvider {
        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return Optional.of(new AuthenticatedSession(account.id(), Instant.EPOCH));
        }

        @Override
        public Optional<Account> currentAccount() {
            return Optional.of(account);
        }
    }
}

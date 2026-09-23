package com.company.incidentdesk.ui.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.AuthenticationResult;
import com.company.incidentdesk.application.session.SessionService;
import com.company.incidentdesk.domain.account.Account;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

class AuthenticationPageTest {
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
    void loginUsesCredentialsWithoutOfferingRoleSelectionAndClearsPassword() throws Exception {
        onFx(() -> {
            RecordingSessions sessions = new RecordingSessions();
            AtomicInteger authenticated = new AtomicInteger();
            AuthenticationPage page = new AuthenticationPage(
                    sessions, authenticated::incrementAndGet, () -> { }, () -> { });
            new Scene(page);
            ((TextField) page.lookup("#login-name")).setText("CaseSensitiveUser");
            PasswordField password = (PasswordField) page.lookup("#password");
            password.setText("secret");
            ((Button) page.lookup("#login")).fire();

            assertEquals("CaseSensitiveUser", sessions.loginName);
            assertEquals("secret", sessions.password);
            assertTrue(password.getText().isEmpty());
            assertEquals(1, authenticated.get());
            assertTrue(page.lookupAll(".role-button").isEmpty());
            return null;
        });
    }

    @Test
    void registrationEntryPointProvidesExplicitFeedback() throws Exception {
        onFx(() -> {
            AtomicInteger registrations = new AtomicInteger();
            AuthenticationPage page = new AuthenticationPage(
                    new RecordingSessions(), () -> { }, registrations::incrementAndGet, () -> { });
            new Scene(page);
            ((Button) page.lookup("#register")).fire();
            assertEquals(1, registrations.get());
            assertTrue(page.lookupAll(".feedback-card").stream()
                    .flatMap(node -> node.lookupAll(".label").stream())
                    .map(node -> ((Label) node).getText())
                    .anyMatch("Registration is not available yet"::equals));
            return null;
        });
    }

    @Test
    void authenticationFormUsesCenteredPanelWithLeftAlignedFields() throws Exception {
        onFx(() -> {
            AuthenticationPage page = new AuthenticationPage(
                    new RecordingSessions(), () -> { }, () -> { }, () -> { });
            new Scene(page, 900, 700);
            page.applyCss();
            page.layout();

            VBox panel = (VBox) page.lookup(".panel");
            TextField loginName = (TextField) page.lookup("#login-name");
            PasswordField password = (PasswordField) page.lookup("#password");
            VBox loginField = (VBox) loginName.getParent();
            VBox passwordField = (VBox) password.getParent();
            VBox form = (VBox) loginField.getParent();

            assertEquals(Pos.TOP_CENTER, panel.getAlignment());
            assertEquals(Double.MAX_VALUE, panel.getMaxWidth());
            assertEquals(Double.MAX_VALUE, form.getMaxWidth());
            assertEquals(Pos.TOP_LEFT, loginField.getAlignment());
            assertEquals(Pos.TOP_LEFT, passwordField.getAlignment());
            assertEquals(320, loginField.getMaxWidth());
            assertEquals(320, passwordField.getMaxWidth());
            assertEquals(Pos.CENTER_LEFT, loginName.getAlignment());
            assertEquals(Pos.CENTER_LEFT, password.getAlignment());
            return null;
        });
    }

    @Test
    void sampleUiButtonUsesDedicatedNavigationCallback() throws Exception {
        onFx(() -> {
            AtomicInteger sampleUiRequests = new AtomicInteger();
            AuthenticationPage page = new AuthenticationPage(
                    new RecordingSessions(), () -> { }, () -> { }, sampleUiRequests::incrementAndGet);
            new Scene(page);

            ((Button) page.lookup("#sample-ui")).fire();

            assertEquals(1, sampleUiRequests.get());
            return null;
        });
    }

    private static <T> T onFx(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static final class RecordingSessions implements SessionService {
        private String loginName;
        private String password;

        @Override
        public AuthenticationResult login(String suppliedLoginName, char[] suppliedPassword) {
            loginName = suppliedLoginName;
            password = new String(suppliedPassword);
            return AuthenticationResult.AUTHENTICATED;
        }

        @Override
        public void logout() {
        }

        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return Optional.empty();
        }

        @Override
        public Optional<Account> currentAccount() {
            return Optional.empty();
        }
    }
}

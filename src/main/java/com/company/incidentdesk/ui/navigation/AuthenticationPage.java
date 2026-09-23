package com.company.incidentdesk.ui.navigation;

import java.util.Arrays;
import java.util.Objects;

import com.company.incidentdesk.application.session.AuthenticationResult;
import com.company.incidentdesk.application.session.SessionService;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.UiComponents;
import com.company.incidentdesk.ui.shared.components.ValidatedField;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

/** Authentication entry page; role is always derived from the authenticated account. */
public final class AuthenticationPage extends VBox {
    private final TextField loginName = new TextField();
    private final PasswordField password = new PasswordField();
    private final VBox feedback = new VBox();

    public AuthenticationPage(
            SessionService sessions,
            Runnable onAuthenticated,
            Runnable onRegister,
            Runnable onShowSampleUi) {
        super(16);
        SessionService sessionService = Objects.requireNonNull(sessions, "sessions");
        Objects.requireNonNull(onAuthenticated, "onAuthenticated");
        Objects.requireNonNull(onRegister, "onRegister");
        Objects.requireNonNull(onShowSampleUi, "onShowSampleUi");

        Label title = new Label("Incident Desk");
        title.getStyleClass().add("page-title");
        title.setTextAlignment(TextAlignment.CENTER);
        Label prompt = new Label("Sign in to your company account");
        prompt.setTextAlignment(TextAlignment.CENTER);
        loginName.setPromptText("Case-sensitive login name");
        loginName.setId("login-name");
        loginName.setAlignment(Pos.CENTER_LEFT);
        loginName.setMaxWidth(Double.MAX_VALUE);
        password.setPromptText("Password");
        password.setId("password");
        password.setAlignment(Pos.CENTER_LEFT);
        password.setMaxWidth(Double.MAX_VALUE);
        Button login = UiComponents.action("Sign in", ActionStyle.PRIMARY);
        login.setId("login");
        login.setDefaultButton(true);
        login.setOnAction(event -> authenticate(sessionService, onAuthenticated));
        Button register = UiComponents.action("Register", ActionStyle.SECONDARY);
        register.setId("register");
        register.setOnAction(event -> {
            onRegister.run();
            feedback.getChildren().setAll(UiComponents.feedback(
                    "Registration is not available yet",
                    "Account registration will appear here when credential storage is connected.",
                    FeedbackType.EMPTY));
        });
        Button sampleUi = UiComponents.action("Sample UI", ActionStyle.SECONDARY);
        sampleUi.setId("sample-ui");
        sampleUi.setOnAction(event -> onShowSampleUi.run());
        FlowPane actions = new FlowPane(10, 10, login, register, sampleUi);
        actions.setAlignment(Pos.CENTER);
        ValidatedField loginField = UiComponents.field("Login name", loginName);
        loginField.setAlignment(Pos.TOP_LEFT);
        loginField.setMaxWidth(320);
        ValidatedField passwordField = UiComponents.field("Password", password);
        passwordField.setAlignment(Pos.TOP_LEFT);
        passwordField.setMaxWidth(320);
        VBox form = new VBox(14,
                loginField,
                passwordField,
                actions,
                feedback);
        form.setMaxWidth(Double.MAX_VALUE);
        form.setAlignment(Pos.CENTER);
        feedback.setAlignment(Pos.CENTER);
        VBox authenticationPanel = UiComponents.panel("Authentication", form);
        authenticationPanel.setAlignment(Pos.TOP_CENTER);
        authenticationPanel.setMaxWidth(600);
        getChildren().addAll(title, prompt, authenticationPanel);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(32));
        getStyleClass().add("authentication-page");
    }

    private void authenticate(SessionService sessions, Runnable onAuthenticated) {
        char[] candidate = password.getText().toCharArray();
        password.clear();
        try {
            if (sessions.login(loginName.getText(), candidate) == AuthenticationResult.AUTHENTICATED) {
                feedback.getChildren().clear();
                onAuthenticated.run();
                return;
            }
        } finally {
            Arrays.fill(candidate, '\0');
        }
        feedback.getChildren().setAll(UiComponents.feedback(
                "Sign-in failed",
                "Check your login name and password, then try again.",
                FeedbackType.ERROR));
    }
}

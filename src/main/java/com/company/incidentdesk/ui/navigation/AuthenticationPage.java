package com.company.incidentdesk.ui.navigation;

import java.util.Arrays;
import java.util.Objects;

import com.company.incidentdesk.application.session.AuthenticationResult;
import com.company.incidentdesk.application.session.SessionService;
import com.company.incidentdesk.application.account.AccountRegistrar;
import com.company.incidentdesk.application.account.RegistrationResult;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.UiComponents;
import com.company.incidentdesk.ui.shared.components.ValidatedField;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
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
            AccountRegistrar registrations,
            Runnable onAuthenticated,
            Runnable onShowSampleUi) {
        super(16);
        SessionService sessionService = Objects.requireNonNull(sessions, "sessions");
        Objects.requireNonNull(onAuthenticated, "onAuthenticated");
        Objects.requireNonNull(registrations, "registrations");
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
        register.setOnAction(event -> showRegistration(registrations));
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

    private void showRegistration(AccountRegistrar registrations) {
        Dialog<ButtonType> dialog = createRegistrationDialog(registrations);
        dialog.show();
    }

    private Dialog<ButtonType> createRegistrationDialog(AccountRegistrar registrations) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Register account");
        dialog.setHeaderText("Choose an account type and credentials");
        TextField name = new TextField();
        name.setId("registration-login-name");
        PasswordField secret = new PasswordField();
        secret.setId("registration-password");
        ToggleGroup roles = new ToggleGroup();
        FlowPane options = new FlowPane(10, 10,
                roleOption("Reporter", Role.REPORTER, roles, true),
                roleOption("Responder", Role.RESPONDER, roles, false),
                roleOption("Admin", Role.ADMINISTRATOR, roles, false));
        VBox content = new VBox(14, UiComponents.field("Login name", name),
                UiComponents.field("Password", secret), new Label("Account type"), options);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setOnShown(event -> name.requestFocus());
        dialog.setOnHidden(event -> {
            if (ButtonType.OK.equals(dialog.getResult())) {
                submitRegistration(registrations, name, secret, roles);
            }
        });
        return dialog;
    }

    private void submitRegistration(
            AccountRegistrar registrations,
            TextField name,
            PasswordField secret,
            ToggleGroup roles) {
        char[] candidate = secret.getText().toCharArray();
        secret.clear();
        RegistrationResult result;
        try {
            Role role = (Role) roles.getSelectedToggle().getUserData();
            result = registrations.register(name.getText(), candidate, role);
        } finally {
            Arrays.fill(candidate, '\0');
        }
        feedback.getChildren().setAll(registrationFeedback(result));
    }

    private static RadioButton roleOption(String label, Role role, ToggleGroup group, boolean selected) {
        RadioButton option = new RadioButton(label);
        option.setId("role-" + role.name().toLowerCase());
        option.setToggleGroup(group);
        option.setUserData(role);
        option.setSelected(selected);
        return option;
    }

    private static VBox registrationFeedback(RegistrationResult result) {
        return switch (result) {
        case REGISTERED -> UiComponents.feedback("Account created", "You can now sign in.", FeedbackType.SUCCESS);
        case INVALID_LOGIN_NAME -> registrationFailure("Enter a non-blank login name.");
        case INVALID_PASSWORD -> registrationFailure("Enter a password.");
        case DUPLICATE_LOGIN_NAME -> registrationFailure(
                "That case-sensitive login name is already registered.");
        case FAILED -> registrationFailure("The account could not be created. Try again.");
        };
    }

    private static VBox registrationFailure(String message) {
        return UiComponents.feedback("Registration failed", message, FeedbackType.ERROR);
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

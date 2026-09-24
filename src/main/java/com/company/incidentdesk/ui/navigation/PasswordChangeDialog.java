package com.company.incidentdesk.ui.navigation;

import java.util.Arrays;
import java.util.Objects;

import com.company.incidentdesk.application.account.PasswordChangeResult;
import com.company.incidentdesk.application.account.PasswordChanger;
import com.company.incidentdesk.ui.shared.components.UiComponents;
import com.company.incidentdesk.ui.shared.components.ValidatedField;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Password replacement dialog launched from the authenticated identity control. */
final class PasswordChangeDialog extends Dialog<ButtonType> {
    private final PasswordChanger passwords;
    private final PasswordField currentPassword = passwordField("current-password");
    private final PasswordField newPassword = passwordField("new-password");
    private final PasswordField confirmation = passwordField("confirm-password");
    private final ValidatedField currentField = UiComponents.field("Current password", currentPassword);
    private final ValidatedField newField = UiComponents.field("New password", newPassword);
    private final ValidatedField confirmationField = UiComponents.field("Confirm new password", confirmation);
    private final Label status = new Label();

    PasswordChangeDialog(PasswordChanger passwords) {
        this(passwords, false);
    }

    PasswordChangeDialog(PasswordChanger passwords, boolean required) {
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        setTitle("Update password");
        setHeaderText(required ? "Replace your temporary password to continue" : "Choose a new password");
        status.setWrapText(true);
        status.setManaged(false);
        status.setVisible(false);
        VBox content = new VBox(12, currentField, newField, confirmationField, status);
        content.setPrefWidth(360);
        getDialogPane().setContent(content);
        getDialogPane().setAccessibleText("Update your account password");
        ButtonType update = new ButtonType("Update password", ButtonData.OK_DONE);
        if (required) {
            getDialogPane().getButtonTypes().add(update);
        } else {
            getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, update);
        }
        Button updateButton = (Button) getDialogPane().lookupButton(update);
        updateButton.setId("confirm-password-change");
        updateButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!submit()) {
                event.consume();
            }
        });
        setOnHidden(event -> clearSecrets());
    }

    private boolean submit() {
        clearValidation();
        char[] current = currentPassword.getText().toCharArray();
        char[] replacement = newPassword.getText().toCharArray();
        char[] repeated = confirmation.getText().toCharArray();
        clearSecrets();
        try {
            PasswordChangeResult result = passwords.changePassword(current, replacement, repeated);
            return switch (result) {
            case CHANGED -> true;
            case INVALID_CURRENT_PASSWORD -> showError(currentField, "Current password is incorrect.");
            case INVALID_NEW_PASSWORD -> showError(newField, "Enter a new password.");
            case NEW_PASSWORD_MISMATCH -> showError(confirmationField, "New passwords do not match.");
            case FAILED -> showFailure();
            };
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(replacement, '\0');
            Arrays.fill(repeated, '\0');
        }
    }

    private boolean showError(ValidatedField field, String message) {
        field.showError(message);
        resizeToContent();
        return false;
    }

    private boolean showFailure() {
        status.setText("The password could not be updated. Try again.");
        status.getStyleClass().add("validation-message");
        status.setManaged(true);
        status.setVisible(true);
        resizeToContent();
        return false;
    }

    private void resizeToContent() {
        getDialogPane().applyCss();
        getDialogPane().layout();
        Window window = getDialogPane().getScene().getWindow();
        if (window != null) {
            window.sizeToScene();
        }
    }

    private void clearValidation() {
        currentField.clearError();
        newField.clearError();
        confirmationField.clearError();
        status.setManaged(false);
        status.setVisible(false);
    }

    private void clearSecrets() {
        currentPassword.clear();
        newPassword.clear();
        confirmation.clear();
    }

    private static PasswordField passwordField(String id) {
        PasswordField field = new PasswordField();
        field.setId(id);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }
}

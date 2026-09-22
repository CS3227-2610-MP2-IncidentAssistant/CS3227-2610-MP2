package com.company.incidentdesk.ui.shared.components;

import java.util.Objects;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** A labelled form control with an accessible, managed validation message. */
public final class ValidatedField extends VBox {
    private final Node control;
    private final Label fieldLabel;
    private final Label errorLabel;
    private final String originalAccessibleText;

    public ValidatedField(String labelText, Node control) {
        super(7);
        this.control = Objects.requireNonNull(control, "control");
        originalAccessibleText = control.getAccessibleText();

        fieldLabel = new Label(Objects.requireNonNull(labelText, "labelText"));
        fieldLabel.setLabelFor(control);
        errorLabel = new Label();
        errorLabel.getStyleClass().add("field-error");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
        getChildren().addAll(fieldLabel, control, errorLabel);
    }

    public void showError(String message) {
        String requiredMessage = Objects.requireNonNull(message, "message");
        if (requiredMessage.isBlank()) {
            throw new IllegalArgumentException("Validation message must not be blank");
        }
        if (!control.getStyleClass().contains("invalid")) {
            control.getStyleClass().add("invalid");
        }
        errorLabel.setText(requiredMessage);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
        control.setAccessibleText(labelWithError(requiredMessage));
    }

    public void clearError() {
        control.getStyleClass().remove("invalid");
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
        control.setAccessibleText(originalAccessibleText);
    }

    private String labelWithError(String message) {
        return fieldLabel.getText() + ". " + message;
    }
}

package com.company.incidentdesk.ui.reporter;

import java.util.Objects;
import java.util.function.Consumer;

import com.company.incidentdesk.application.validation.RequiredTextValidator;
import com.company.incidentdesk.application.validation.ValidationField;
import com.company.incidentdesk.application.validation.ValidationResult;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.UiComponents;
import com.company.incidentdesk.ui.shared.components.ValidatedField;

import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Collects the explanation required to reopen a resolved incident. */
final class ReporterFollowUpForm extends VBox {
    private static final ValidationField EXPLANATION = new ValidationField("incident.reopenExplanation");

    private final TextArea explanation = new TextArea();
    private final ValidatedField explanationField;
    private final Button submit = UiComponents.action("Submit follow-up", ActionStyle.PRIMARY);
    private final Button cancel = UiComponents.action("Cancel", ActionStyle.SECONDARY);
    private final RequiredTextValidator requiredText = new RequiredTextValidator();

    ReporterFollowUpForm(Consumer<String> onSubmit, Runnable onCancel) {
        super(12);
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onCancel, "onCancel");
        explanation.setId("reporter-follow-up");
        explanation.setAccessibleText("Follow-up explanation");
        explanation.setPromptText("Explain why the incident has not been resolved.");
        explanation.setPrefRowCount(3);
        explanation.setWrapText(true);
        explanationField = UiComponents.field("Follow-up explanation", explanation);
        submit.setOnAction(event -> {
            ValidationResult validation = requiredText.validate(EXPLANATION, explanation.getText());
            if (!validation.isValid()) {
                showRequiredError();
            } else {
                explanationField.clearError();
                onSubmit.accept(explanation.getText());
            }
        });
        cancel.setOnAction(event -> onCancel.run());
        getChildren().add(UiComponents.panel("Reopen incident", explanationField,
                new FlowPane(8, 8, submit, cancel)));
    }

    void focusExplanation() {
        explanation.requestFocus();
    }

    void showRequiredError() {
        explanationField.showError("A follow-up explanation is required.");
        focusExplanation();
    }

    void clearError() {
        explanationField.clearError();
    }

    void setPending(boolean pending) {
        explanation.setDisable(pending);
        submit.setDisable(pending);
        cancel.setDisable(pending);
    }

    void setUnavailable() {
        explanation.setEditable(false);
        submit.setDisable(true);
    }

    void clear() {
        explanation.clear();
        explanationField.clearError();
    }
}

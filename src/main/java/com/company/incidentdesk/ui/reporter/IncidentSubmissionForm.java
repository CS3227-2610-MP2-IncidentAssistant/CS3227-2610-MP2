package com.company.incidentdesk.ui.reporter;

import java.util.Objects;
import java.util.function.Consumer;

import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import com.company.incidentdesk.application.validation.RequiredTextValidator;
import com.company.incidentdesk.application.validation.ValidationError;
import com.company.incidentdesk.application.validation.ValidationErrorCode;
import com.company.incidentdesk.application.validation.ValidationField;
import com.company.incidentdesk.application.validation.ValidationResult;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.UiComponents;
import com.company.incidentdesk.ui.shared.components.ValidatedField;

/** Reporter-owned incident submission form behavior. */
public final class IncidentSubmissionForm extends VBox {
    static final ValidationField TITLE = new ValidationField("title");
    static final ValidationField DESCRIPTION = new ValidationField("description");
    static final ValidationField CATEGORY = new ValidationField("category");
    private final TextField title = new TextField();
    private final TextArea description = new TextArea();
    private final ComboBox<IncidentCategory> category = new ComboBox<>(
            FXCollections.observableArrayList(IncidentCategory.values()));
    private final ValidatedField titleField;
    private final ValidatedField descriptionField;
    private final ValidatedField categoryField;

    /** Creates the MVP reporter submission form. */
    public IncidentSubmissionForm(Consumer<Submission> onSubmit) {
        super(14);
        Consumer<Submission> requiredOnSubmit = Objects.requireNonNull(onSubmit, "onSubmit");

        title.setId("incident-title");
        title.setPromptText("Brief incident title");
        title.setAccessibleText("Incident title");

        description.setId("incident-description");
        description.setPromptText("Describe what happened");
        description.setWrapText(true);
        description.setAccessibleText("Incident description");

        category.setId("incident-category");
        category.setPromptText("Select a category");
        category.setAccessibleText("Incident category");
        category.setConverter(categoryConverter());

        titleField = UiComponents.field("Title", title);
        descriptionField = UiComponents.field("Description", description);
        categoryField = UiComponents.field("Category", category);

        Button submit = UiComponents.action("Submit incident", ActionStyle.PRIMARY);
        submit.setId("submit-incident");
        submit.setOnAction(event -> {
            ValidationResult validation = validateAndSubmit(
                    new Submission(title.getText(), description.getText(), category.getValue()),
                    requiredOnSubmit);
            showValidation(titleField, validation, TITLE, "Title is required.");
            showValidation(descriptionField, validation, DESCRIPTION, "Description is required.");
            showValidation(categoryField, validation, CATEGORY, "Category is required.");
        });

        getChildren().addAll(
                titleField,
                descriptionField,
                categoryField,
                submit);
    }

    void clearAfterSuccess() {
        title.clear();
        description.clear();
        category.setValue(null);
        showServiceValidation(ValidationResult.valid());
    }

    void showServiceValidation(ValidationResult validation) {
        showValidation(titleField, validation, TITLE, "Title is required.");
        showValidation(descriptionField, validation, DESCRIPTION, "Description is required.");
        showValidation(categoryField, validation, CATEGORY, "Category is required.");
    }

    static ValidationResult validateAndSubmit(Submission submission, Consumer<Submission> onSubmit) {
        Submission requiredSubmission = Objects.requireNonNull(submission, "submission");
        Consumer<Submission> requiredOnSubmit = Objects.requireNonNull(onSubmit, "onSubmit");
        RequiredTextValidator validator = new RequiredTextValidator();
        ValidationResult validation = validator.validate(TITLE, requiredSubmission.title())
                .combine(validator.validate(DESCRIPTION, requiredSubmission.description()))
                .combine(requiredSubmission.category() == null
                        ? ValidationResult.invalid(new ValidationError(CATEGORY, ValidationErrorCode.REQUIRED))
                        : ValidationResult.valid());
        if (validation.isValid()) {
            requiredOnSubmit.accept(requiredSubmission);
        }
        return validation;
    }

    /** Values entered into the MVP incident submission form. */
    public record Submission(String title, String description, IncidentCategory category) {
        public Submission {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
        }
    }

    private static StringConverter<IncidentCategory> categoryConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(IncidentCategory category) {
                return category == null ? "" : category.displayName();
            }

            @Override
            public IncidentCategory fromString(String value) {
                return null;
            }
        };
    }

    private static void showValidation(
            ValidatedField field,
            ValidationResult validation,
            ValidationField validationField,
            String message) {
        if (validation.errorsFor(validationField).isEmpty()) {
            field.clearError();
        } else {
            field.showError(message);
        }
    }
}

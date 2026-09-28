package com.company.incidentdesk.ui.reporter;

import java.util.Objects;
import java.util.function.Consumer;

import com.company.incidentdesk.application.validation.RequiredTextValidator;
import com.company.incidentdesk.application.validation.ValidationError;
import com.company.incidentdesk.application.validation.ValidationErrorCode;
import com.company.incidentdesk.application.validation.ValidationField;
import com.company.incidentdesk.application.validation.ValidationResult;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.UiComponents;
import com.company.incidentdesk.ui.shared.components.ValidatedField;

import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Edits the mutable fields of a Reporter-owned, unassigned incident. */
final class ReporterEditForm extends VBox {
    private static final ValidationField TITLE = new ValidationField("incident.title");
    private static final ValidationField DESCRIPTION = new ValidationField("incident.description");
    private static final ValidationField CATEGORY = new ValidationField("incident.category");

    private final TextField title = new TextField();
    private final TextArea description = new TextArea();
    private final ComboBox<IncidentCategory> category = new ComboBox<>(
            FXCollections.observableArrayList(IncidentCategory.values()));
    private final ValidatedField titleField;
    private final ValidatedField descriptionField;
    private final ValidatedField categoryField;
    private final Button save = UiComponents.action("Save changes", ActionStyle.PRIMARY);
    private final Button cancel = UiComponents.action("Cancel", ActionStyle.SECONDARY);
    private final RequiredTextValidator requiredText = new RequiredTextValidator();
    private final boolean anonymous;

    ReporterEditForm(String originalTitle, String originalDescription, IncidentCategory originalCategory,
            boolean anonymous, Consumer<Changes> onSave, Runnable onCancel) {
        super(12);
        Objects.requireNonNull(onSave, "onSave");
        Objects.requireNonNull(onCancel, "onCancel");

        title.setId("reporter-edit-title");
        title.setAccessibleText("Incident title");
        title.setText(Objects.requireNonNull(originalTitle, "originalTitle"));
        description.setId("reporter-edit-description");
        description.setAccessibleText("Incident description");
        description.setWrapText(true);
        description.setPrefRowCount(3);
        description.setText(Objects.requireNonNull(originalDescription, "originalDescription"));
        category.setId("reporter-edit-category");
        category.setAccessibleText("Incident category");
        category.setConverter(categoryConverter());
        category.setValue(Objects.requireNonNull(originalCategory, "originalCategory"));
        this.anonymous = anonymous;

        titleField = UiComponents.field("Title", title);
        descriptionField = UiComponents.field("Description", description);
        categoryField = UiComponents.field("Category", category);
        save.setOnAction(event -> submit(onSave));
        cancel.setOnAction(event -> onCancel.run());
        getChildren().add(UiComponents.panel("Edit incident", titleField, descriptionField, categoryField,
                new FlowPane(8, 8, save, cancel)));
    }

    private void submit(Consumer<Changes> onSave) {
        ValidationResult validation = requiredText.validate(TITLE, title.getText())
                .combine(requiredText.validate(DESCRIPTION, description.getText()))
                .combine(category.getValue() == null
                        ? ValidationResult.invalid(new ValidationError(CATEGORY, ValidationErrorCode.REQUIRED))
                        : ValidationResult.valid());
        showValidation(validation);
        if (validation.isValid()) {
            onSave.accept(new Changes(title.getText(), description.getText(), category.getValue(), anonymous));
        }
    }

    void showValidation(ValidationResult validation) {
        showValidation(titleField, validation, TITLE, "Title is required.");
        showValidation(descriptionField, validation, DESCRIPTION, "Description is required.");
        showValidation(categoryField, validation, CATEGORY, "Category is required.");
    }

    void setPending(boolean pending) {
        title.setDisable(pending);
        description.setDisable(pending);
        category.setDisable(pending);
        save.setDisable(pending);
        cancel.setDisable(pending);
    }

    void setUnavailable() {
        title.setEditable(false);
        description.setEditable(false);
        category.setDisable(true);
        save.setDisable(true);
    }

    void focusTitle() {
        title.requestFocus();
    }

    void clear() {
        title.clear();
        description.clear();
        category.setValue(null);
        showValidation(ValidationResult.valid());
    }

    record Changes(String title, String description, IncidentCategory category, boolean anonymous) { }

    private static StringConverter<IncidentCategory> categoryConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(IncidentCategory value) {
                return value == null ? "" : value.displayName();
            }

            @Override
            public IncidentCategory fromString(String value) {
                return null;
            }
        };
    }

    private static void showValidation(
            ValidatedField field, ValidationResult validation, ValidationField name, String message) {
        if (validation.errorsFor(name).isEmpty()) {
            field.clearError();
        } else {
            field.showError(message);
        }
    }
}

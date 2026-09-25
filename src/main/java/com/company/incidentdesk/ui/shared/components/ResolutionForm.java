package com.company.incidentdesk.ui.shared.components;

import java.util.function.Consumer;

import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Shared resolution composition; application results drive validation feedback. */
public final class ResolutionForm extends VBox {
    private final TextArea remarks = new TextArea();
    private final ValidatedField field = UiComponents.field("Resolution remarks", remarks);
    private final Button submit = UiComponents.action("Confirm resolution", ActionStyle.PRIMARY);
    private final Button cancel = UiComponents.action("Cancel", ActionStyle.SECONDARY);

    public ResolutionForm(Consumer<String> onSubmit, Runnable onCancel) {
        remarks.setId("resolution-remarks");
        remarks.setAccessibleText("Resolution remarks");
        remarks.setWrapText(true);
        remarks.setPrefRowCount(3);
        remarks.setPromptText("Explain how the incident was resolved.");
        submit.setOnAction(event -> onSubmit.accept(remarks.getText()));
        cancel.setOnAction(event -> onCancel.run());
        getChildren().add(UiComponents.panel("Resolve incident", field, new FlowPane(8, 8, submit, cancel)));
    }

    public void focusRemarks() { remarks.requestFocus(); }

    public void setPending(boolean pending) {
        remarks.setDisable(pending);
        submit.setDisable(pending);
        cancel.setDisable(pending);
    }

    public void clearError() { field.clearError(); }

    public void showRequiredError() {
        field.showError("Resolution remarks are required.");
        focusRemarks();
    }

    public void clear() {
        remarks.clear();
        field.clearError();
    }
}

package com.company.incidentdesk.ui.responder;

import java.util.function.Consumer;

import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.UiComponents;
import com.company.incidentdesk.ui.shared.components.ValidatedField;

import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Responder composition of shared controls; application results drive validation feedback. */
final class ResolutionForm extends VBox {
    private final TextArea remarks = new TextArea();
    private final ValidatedField field = UiComponents.field("Resolution remarks", remarks);
    private final Button submit = UiComponents.action("Confirm resolution", ActionStyle.PRIMARY);
    private final Button cancel = UiComponents.action("Cancel", ActionStyle.SECONDARY);

    ResolutionForm(Consumer<String> onSubmit, Runnable onCancel) {
        remarks.setId("resolution-remarks");
        remarks.setAccessibleText("Resolution remarks");
        remarks.setWrapText(true);
        remarks.setPrefRowCount(3);
        remarks.setPromptText("Explain how the incident was resolved.");
        submit.setOnAction(event -> onSubmit.accept(remarks.getText()));
        cancel.setOnAction(event -> onCancel.run());
        getChildren().add(UiComponents.panel("Resolve incident", field, new FlowPane(8, 8, submit, cancel)));
    }

    void focusRemarks() { remarks.requestFocus(); }

    void setPending(boolean pending) {
        remarks.setDisable(pending);
        submit.setDisable(pending);
        cancel.setDisable(pending);
    }

    void clearError() { field.clearError(); }

    void showRequiredError() {
        field.showError("Resolution remarks are required.");
        focusRemarks();
    }

    void clear() {
        remarks.clear();
        field.clearError();
    }
}

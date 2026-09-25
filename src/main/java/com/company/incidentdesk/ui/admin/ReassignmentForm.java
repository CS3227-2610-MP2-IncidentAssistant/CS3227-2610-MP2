package com.company.incidentdesk.ui.admin;

import java.util.List;
import java.util.function.Consumer;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.IncidentFilterBar.AccountOption;
import com.company.incidentdesk.ui.shared.components.UiComponents;
import com.company.incidentdesk.ui.shared.components.ValidatedField;

import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Administrator composition for picking an eligible responder to (re)assign an incident to. */
final class ReassignmentForm extends VBox {
    private final ComboBox<AccountOption> responder = new ComboBox<>();
    private final ValidatedField field = UiComponents.field("Responder", responder);
    private final Button submit = UiComponents.action("Confirm reassignment", ActionStyle.PRIMARY);
    private final Button cancel = UiComponents.action("Cancel", ActionStyle.SECONDARY);
    private boolean pending;

    ReassignmentForm(List<AccountOption> eligibleResponders, Consumer<AccountId> onSubmit, Runnable onCancel) {
        responder.setId("reassignment-responder");
        responder.setAccessibleText("Responder");
        responder.setPromptText("Choose a responder");
        responder.setItems(FXCollections.observableArrayList(eligibleResponders));
        responder.valueProperty().addListener((observable, previous, current) -> updateSubmitDisabled());
        submit.setOnAction(event -> {
            AccountOption selected = responder.getValue();
            if (selected != null) {
                onSubmit.accept(selected.id());
            }
        });
        cancel.setOnAction(event -> onCancel.run());
        updateSubmitDisabled();
        getChildren().add(UiComponents.panel("Reassign incident", field, new FlowPane(8, 8, submit, cancel)));
    }

    void setPending(boolean pending) {
        this.pending = pending;
        responder.setDisable(pending);
        cancel.setDisable(pending);
        updateSubmitDisabled();
    }

    private void updateSubmitDisabled() {
        submit.setDisable(pending || responder.getValue() == null);
    }

    void clearError() { field.clearError(); }

    void clear() {
        responder.setValue(null);
        field.clearError();
    }
}

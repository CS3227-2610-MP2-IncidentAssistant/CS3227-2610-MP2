package com.company.incidentdesk.ui.shared.components;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.company.incidentdesk.application.presentation.IncidentActionModel;
import com.company.incidentdesk.domain.incident.IncidentId;

import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;

/** Renders available workflow actions from authorized presentation flags. */
public final class IncidentActionBar extends FlowPane {
    public IncidentActionBar(IncidentId incidentId, IncidentActionModel available, IncidentDetailActions actions) {
        super(8, 8);
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(actions, "actions");
        getStyleClass().add("incident-action-bar");
        add("Edit", available.edit(), actions.edit(), incidentId, ActionStyle.SECONDARY);
        add("Withdraw", available.withdraw(), actions.withdraw(), incidentId, ActionStyle.DANGER);
        add("Claim", available.claim(), actions.claim(), incidentId, ActionStyle.PRIMARY);
        add("Resolve", available.resolve(), actions.resolve(), incidentId, ActionStyle.PRIMARY);
        add("Hand off", available.handoff(), actions.handoff(), incidentId, ActionStyle.SECONDARY);
        add("Reassign", available.reassign(), actions.reassign(), incidentId, ActionStyle.SECONDARY);
        add("Reopen", available.reopen(), actions.reopen(), incidentId, ActionStyle.PRIMARY);
    }

    private void add(String label, boolean visible,
            Optional<Consumer<IncidentId>> callback,
            IncidentId id, ActionStyle style) {
        if (!visible) {
            return;
        }
        Button button = UiComponents.action(label, style);
        if (callback.isPresent()) {
            button.setOnAction(event -> callback.orElseThrow().accept(id));
        } else {
            button.setDisable(true);
            button.setAccessibleHelp("This workflow action is not connected yet.");
        }
        getChildren().add(button);
    }
}

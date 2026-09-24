package com.company.incidentdesk.ui.shared.components;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.company.incidentdesk.domain.incident.IncidentId;

/** Typed workflow hooks supplied by role-specific detail controllers. */
public record IncidentDetailActions(
        Optional<Consumer<IncidentId>> edit,
        Optional<Consumer<IncidentId>> withdraw,
        Optional<Consumer<IncidentId>> claim,
        Optional<Consumer<IncidentId>> resolve,
        Optional<Consumer<IncidentId>> handoff,
        Optional<Consumer<IncidentId>> reassign,
        Optional<Consumer<IncidentId>> reopen) {
    public IncidentDetailActions {
        Objects.requireNonNull(edit, "edit");
        Objects.requireNonNull(withdraw, "withdraw");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(resolve, "resolve");
        Objects.requireNonNull(handoff, "handoff");
        Objects.requireNonNull(reassign, "reassign");
        Objects.requireNonNull(reopen, "reopen");
    }

    public static IncidentDetailActions none() {
        return new IncidentDetailActions(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }
}

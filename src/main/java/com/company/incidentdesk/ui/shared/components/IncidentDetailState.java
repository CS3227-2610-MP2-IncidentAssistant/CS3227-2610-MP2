package com.company.incidentdesk.ui.shared.components;

import java.util.Objects;

import com.company.incidentdesk.application.presentation.IncidentDetailModel;

/** Explicit loading, ready, and neutral unavailable states for the shared detail view. */
public sealed interface IncidentDetailState
        permits IncidentDetailState.Loading, IncidentDetailState.Ready, IncidentDetailState.Unavailable {
    record Loading() implements IncidentDetailState { }

    record Ready(IncidentDetailModel model) implements IncidentDetailState {
        public Ready {
            Objects.requireNonNull(model, "model");
        }
    }

    record Unavailable() implements IncidentDetailState { }

    static IncidentDetailState loading() { return new Loading(); }
    static IncidentDetailState ready(IncidentDetailModel model) { return new Ready(model); }
    static IncidentDetailState unavailable() { return new Unavailable(); }
}

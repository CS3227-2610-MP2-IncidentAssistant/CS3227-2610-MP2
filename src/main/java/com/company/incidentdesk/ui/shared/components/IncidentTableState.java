package com.company.incidentdesk.ui.shared.components;

import java.util.List;
import java.util.Objects;

import com.company.incidentdesk.application.presentation.IncidentRowModel;

/** Complete render state for an incident table load. */
public record IncidentTableState(Status status, List<IncidentRowModel> rows, String errorDetail) {
    public IncidentTableState {
        Objects.requireNonNull(status, "status");
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        errorDetail = Objects.requireNonNull(errorDetail, "errorDetail");
        if (status != Status.CONTENT && !rows.isEmpty()) {
            throw new IllegalArgumentException("Only content state may contain rows");
        }
        if (status == Status.ERROR && errorDetail.isBlank()) {
            throw new IllegalArgumentException("Error state requires detail");
        }
    }

    public static IncidentTableState loading() {
        return new IncidentTableState(Status.LOADING, List.of(), "");
    }

    public static IncidentTableState loaded(List<IncidentRowModel> rows) {
        List<IncidentRowModel> copy = List.copyOf(rows);
        return copy.isEmpty()
                ? new IncidentTableState(Status.EMPTY, List.of(), "")
                : new IncidentTableState(Status.CONTENT, copy, "");
    }

    public static IncidentTableState error(String detail) {
        return new IncidentTableState(Status.ERROR, List.of(), detail);
    }

    public enum Status { LOADING, CONTENT, EMPTY, ERROR }
}

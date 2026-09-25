package com.company.incidentdesk.application.presentation;

import java.util.List;
import java.util.Objects;

/** Privacy-safe administrator incident rows and their authorized identity filters. */
public record AdministratorIncidentListModel(
        List<IncidentRowModel> rows,
        List<IncidentIdentityOptionModel> reporters,
        List<IncidentIdentityOptionModel> responders) {
    public AdministratorIncidentListModel {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        reporters = List.copyOf(Objects.requireNonNull(reporters, "reporters"));
        responders = List.copyOf(Objects.requireNonNull(responders, "responders"));
    }

    public static AdministratorIncidentListModel empty() {
        return new AdministratorIncidentListModel(List.of(), List.of(), List.of());
    }
}

package com.company.incidentdesk.application.presentation;

import java.util.List;

/** Authorized, queue-ordered snapshots without domain incidents or reporter identifiers. */
public record ResponderDashboardModel(List<IncidentRowModel> eligible, List<IncidentRowModel> assigned) {
    public ResponderDashboardModel {
        eligible = List.copyOf(eligible);
        assigned = List.copyOf(assigned);
    }

    public static ResponderDashboardModel empty() {
        return new ResponderDashboardModel(List.of(), List.of());
    }
}

package com.company.incidentdesk.ui.shared.components;

import java.util.List;
import java.util.Objects;

/** Immutable role-specific presentation choices for {@link IncidentTable}. */
public record IncidentTableConfiguration(
        List<IncidentTableColumn> columns,
        boolean identityFiltersVisible,
        boolean sloFilterVisible) {
    public IncidentTableConfiguration {
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        if (columns.stream().distinct().count() != columns.size()) {
            throw new IllegalArgumentException("columns must not contain duplicates");
        }
    }

    public static IncidentTableConfiguration reporter() {
        return new IncidentTableConfiguration(List.of(
                IncidentTableColumn.REFERENCE, IncidentTableColumn.TITLE,
                IncidentTableColumn.CATEGORY, IncidentTableColumn.STATUS,
                IncidentTableColumn.SUBMITTED), false, false);
    }

    public static IncidentTableConfiguration responder() {
        return new IncidentTableConfiguration(List.of(
                IncidentTableColumn.REFERENCE, IncidentTableColumn.TITLE,
                IncidentTableColumn.CATEGORY, IncidentTableColumn.STATUS,
                IncidentTableColumn.REPORTER, IncidentTableColumn.QUEUE_ENTERED,
                IncidentTableColumn.SLO), false, true);
    }

    public static IncidentTableConfiguration administrator() {
        return new IncidentTableConfiguration(List.of(
                IncidentTableColumn.REFERENCE, IncidentTableColumn.TITLE,
                IncidentTableColumn.CATEGORY, IncidentTableColumn.STATUS,
                IncidentTableColumn.SUBMITTED, IncidentTableColumn.REPORTER,
                IncidentTableColumn.ASSIGNEE, IncidentTableColumn.SLO), true, true);
    }
}

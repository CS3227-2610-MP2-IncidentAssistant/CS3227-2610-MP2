package com.company.incidentdesk.application.presentation;

import java.util.Objects;

import com.company.incidentdesk.domain.incident.IncidentId;

/** Immutable, compact incident representation for tables, notifications, and dashboards. */
public record IncidentRowModel(
        IncidentId id,
        String title,
        String categoryLabel,
        String statusLabel,
        String reporterLabel,
        String assigneeLabel,
        String createdAt,
        String queueEnteredAt,
        SloSummaryModel slo,
        boolean anonymous,
        int reopenCount,
        IncidentActionModel actions) {
    public IncidentRowModel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(categoryLabel, "categoryLabel");
        Objects.requireNonNull(statusLabel, "statusLabel");
        Objects.requireNonNull(reporterLabel, "reporterLabel");
        Objects.requireNonNull(assigneeLabel, "assigneeLabel");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(queueEnteredAt, "queueEnteredAt");
        Objects.requireNonNull(slo, "slo");
        Objects.requireNonNull(actions, "actions");
    }
}

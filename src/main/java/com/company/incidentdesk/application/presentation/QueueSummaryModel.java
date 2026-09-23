package com.company.incidentdesk.application.presentation;

import java.util.Objects;

/** Display-ready queue timing for the current lifecycle cycle. */
public record QueueSummaryModel(String enteredAt, String firstAssignedAt, String latestAssignedAt) {
    public QueueSummaryModel {
        Objects.requireNonNull(enteredAt, "enteredAt");
        Objects.requireNonNull(firstAssignedAt, "firstAssignedAt");
        Objects.requireNonNull(latestAssignedAt, "latestAssignedAt");
    }
}

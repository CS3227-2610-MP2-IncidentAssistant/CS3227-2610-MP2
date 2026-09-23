package com.company.incidentdesk.application.presentation;

import java.util.Objects;

/** Immutable SLO status supplied to incident dashboards and detail views. */
public record SloSummaryModel(String label, double progress, boolean overdue) {
    public SloSummaryModel {
        Objects.requireNonNull(label, "label");
        if (!Double.isFinite(progress) || progress < 0) {
            throw new IllegalArgumentException("progress must be finite and non-negative");
        }
    }

    /** Represents an incident for which no configured SLO summary is available. */
    public static SloSummaryModel unavailable() {
        return new SloSummaryModel("Not configured", 0, false);
    }
}

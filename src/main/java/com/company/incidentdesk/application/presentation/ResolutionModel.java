package com.company.incidentdesk.application.presentation;

import java.util.Objects;

/** Presentation-safe result of a completed resolution cycle. */
public record ResolutionModel(int cycleNumber, String remarks, String resolvedAt, String resolvedByLabel) {
    public ResolutionModel {
        if (cycleNumber < 1) {
            throw new IllegalArgumentException("cycleNumber must be positive");
        }
        Objects.requireNonNull(remarks, "remarks");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
        Objects.requireNonNull(resolvedByLabel, "resolvedByLabel");
    }
}

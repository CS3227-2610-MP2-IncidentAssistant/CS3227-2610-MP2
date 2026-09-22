package com.company.incidentdesk.domain.incident;

import java.util.Objects;

/** Incident and required explanation that must be committed together when reopening. */
public record ReopenTransition(Incident incident, ReopenExplanation explanation) {
    /**
     * Creates the indivisible result of a reopen operation.
     *
     * @param incident reopened incident snapshot
     * @param explanation required explanation associated with its new queue cycle
     */
    public ReopenTransition {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(explanation, "explanation");
        if (incident.status() != IncidentStatus.SUBMITTED) {
            throw new IllegalArgumentException("reopened incident must be submitted");
        }
        if (incident.resolutionCycles().size() != explanation.resolutionCycleNumber()) {
            throw new IllegalArgumentException("explanation must reference the current resolution cycle");
        }
        if (!incident.currentCycle().orElseThrow().queueEnteredAt().equals(explanation.createdAt())) {
            throw new IllegalArgumentException("explanation time must equal the reopened queue-entry time");
        }
    }
}

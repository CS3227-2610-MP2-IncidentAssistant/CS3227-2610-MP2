package com.company.incidentdesk.application.incident;

import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.ReopenExplanation;

/** Complete incident-side effects supplied to one atomic audited commit. */
public record IncidentMutation(
        Type type,
        Incident incident,
        Optional<ReopenExplanation> reopenExplanation) {
    public enum Type {
        CREATE,
        UPDATE
    }

    public IncidentMutation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(reopenExplanation, "reopenExplanation");
        reopenExplanation.ifPresent(explanation -> {
            if (!explanation.authorId().equals(incident.reporterId())) {
                throw new IllegalArgumentException("reopen explanation author must own the incident");
            }
        });
    }

    public static IncidentMutation create(Incident incident) {
        return new IncidentMutation(Type.CREATE, incident, Optional.empty());
    }

    public static IncidentMutation update(Incident incident) {
        return new IncidentMutation(Type.UPDATE, incident, Optional.empty());
    }

    public static IncidentMutation reopen(Incident incident, ReopenExplanation explanation) {
        return new IncidentMutation(Type.UPDATE, incident, Optional.of(explanation));
    }
}

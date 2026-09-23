package com.company.incidentdesk.application.incident;

import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.ReopenExplanation;
import com.company.incidentdesk.domain.comment.IncidentComment;

/** Complete incident-side effects supplied to one atomic audited commit. */
public record IncidentMutation(
        Type type,
        Incident incident,
        Optional<ReopenExplanation> reopenExplanation,
        Optional<IncidentComment> comment) {
    public enum Type {
        CREATE,
        UPDATE
    }

    public IncidentMutation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(reopenExplanation, "reopenExplanation");
        Objects.requireNonNull(comment, "comment");
        reopenExplanation.ifPresent(explanation -> {
            if (!explanation.authorId().equals(incident.reporterId())) {
                throw new IllegalArgumentException("reopen explanation author must own the incident");
            }
        });
        comment.ifPresent(value -> {
            if (!value.incidentId().equals(incident.id())) {
                throw new IllegalArgumentException("comment must belong to the incident");
            }
        });
    }

    public static IncidentMutation create(Incident incident) {
        return new IncidentMutation(Type.CREATE, incident, Optional.empty(), Optional.empty());
    }

    public static IncidentMutation update(Incident incident) {
        return new IncidentMutation(Type.UPDATE, incident, Optional.empty(), Optional.empty());
    }

    public static IncidentMutation reopen(
            Incident incident,
            ReopenExplanation explanation,
            IncidentComment comment) {
        return new IncidentMutation(Type.UPDATE, incident, Optional.of(explanation), Optional.of(comment));
    }

    public static IncidentMutation comment(Incident incident, IncidentComment comment) {
        return new IncidentMutation(Type.UPDATE, incident, Optional.empty(), Optional.of(comment));
    }
}

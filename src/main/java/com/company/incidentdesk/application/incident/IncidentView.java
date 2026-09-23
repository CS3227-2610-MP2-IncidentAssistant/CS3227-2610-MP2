package com.company.incidentdesk.application.incident;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentStatus;

/** Presentation-safe incident data without the internal reporter identifier. */
public record IncidentView(
        IncidentId id,
        String title,
        String description,
        IncidentCategory category,
        IncidentStatus status,
        boolean anonymous,
        Instant createdAt,
        Optional<Instant> submittedAt,
        Optional<Instant> withdrawnAt,
        Optional<AccountId> assigneeId,
        int reopenCount) {
    public IncidentView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(withdrawnAt, "withdrawnAt");
        Objects.requireNonNull(assigneeId, "assigneeId");
    }

    public static IncidentView from(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        return new IncidentView(
                incident.id(),
                incident.title(),
                incident.description(),
                incident.category(),
                incident.status(),
                incident.anonymous(),
                incident.createdAt(),
                incident.submittedAt(),
                incident.withdrawnAt(),
                incident.assigneeId(),
                incident.reopenCount());
    }
}

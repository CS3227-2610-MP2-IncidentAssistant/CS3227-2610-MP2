package com.company.incidentdesk.domain.incident;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for an incident. */
public record IncidentId(UUID value) {
    /**
     * Creates an incident identifier.
     *
     * @param value identifier value
     */
    public IncidentId {
        Objects.requireNonNull(value, "value");
    }
}

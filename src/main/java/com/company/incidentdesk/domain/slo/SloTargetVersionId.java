package com.company.incidentdesk.domain.slo;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for one versioned SLO target snapshot. */
public record SloTargetVersionId(UUID value) {
    /**
     * Creates an SLO target version identifier.
     *
     * @param value identifier value
     */
    public SloTargetVersionId {
        Objects.requireNonNull(value, "value");
    }
}

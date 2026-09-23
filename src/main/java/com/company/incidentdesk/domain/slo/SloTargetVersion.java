package com.company.incidentdesk.domain.slo;

import java.time.Instant;
import java.util.Objects;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.IncidentCategory;

/** One versioned, prospectively effective SLO target snapshot for a category. */
public record SloTargetVersion(
        SloTargetVersionId id,
        IncidentCategory category,
        SloTarget target,
        Instant effectiveFrom,
        AccountId changedBy) {
    /**
     * Creates and validates a versioned SLO target snapshot.
     *
     * @param id stable version identifier
     * @param category category this version applies to
     * @param target configured thresholds for this version
     * @param effectiveFrom application-generated UTC time this version becomes effective
     * @param changedBy administrator who made this change
     */
    public SloTargetVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(changedBy, "changedBy");
    }
}

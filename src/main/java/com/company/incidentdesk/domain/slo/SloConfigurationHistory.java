package com.company.incidentdesk.domain.slo;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.incident.IncidentCategory;

/** Chronological, versioned SLO target history used to resolve prospective compliance targets. */
public final class SloConfigurationHistory {
    private static final Comparator<SloTargetVersion> APPLICATION_ORDER =
            Comparator.comparing(SloTargetVersion::effectiveFrom)
                    .thenComparing(version -> version.id().value());

    private final List<SloTargetVersion> versions;

    /**
     * Creates a history over an unordered set of versions.
     *
     * @param versions all known versions across categories
     */
    public SloConfigurationHistory(List<SloTargetVersion> versions) {
        this.versions = List.copyOf(Objects.requireNonNull(versions, "versions"));
    }

    /**
     * Returns the version effective for the category at the given instant.
     *
     * <p>Preserves historical compliance: a later configuration edit never changes which version
     * applied to an earlier lifecycle cycle.
     *
     * @param category category to resolve
     * @param at instant the version must already be effective at or before
     * @return the latest applicable version, or empty when none is configured yet
     */
    public Optional<SloTargetVersion> targetFor(IncidentCategory category, Instant at) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(at, "at");
        return versions.stream()
                .filter(version -> version.category() == category)
                .filter(version -> !version.effectiveFrom().isAfter(at))
                .max(APPLICATION_ORDER);
    }

    /**
     * Returns the most recently configured version for the category, regardless of effective time.
     *
     * @param category category to resolve
     * @return the latest known version, or empty when none is configured yet
     */
    public Optional<SloTargetVersion> latest(IncidentCategory category) {
        Objects.requireNonNull(category, "category");
        return versions.stream()
                .filter(version -> version.category() == category)
                .max(APPLICATION_ORDER);
    }
}

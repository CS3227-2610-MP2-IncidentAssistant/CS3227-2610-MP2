package com.company.incidentdesk.application.slo;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.slo.SloCalculator;
import com.company.incidentdesk.domain.slo.SloConfigurationHistory;
import com.company.incidentdesk.domain.slo.SloStatusModel;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.persistence.IncidentSloClassifier;
import com.company.incidentdesk.persistence.IncidentSloState;
import com.company.incidentdesk.persistence.SloConfigurationStore;

/** Classifies incidents by live SLO compliance using persisted, versioned configuration. */
public final class SloIncidentClassifier implements IncidentSloClassifier {
    private final SloConfigurationStore store;
    private final Clock clock;

    public SloIncidentClassifier(SloConfigurationStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public IncidentSloState classify(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        SloConfigurationHistory history = new SloConfigurationHistory(store.findAll());
        Optional<SloTargetVersion> target = history.targetFor(incident.category(), clock.instant());
        SloStatusModel status = SloCalculator.liveStatus(incident, target, clock.instant());
        return switch (status.state()) {
        case WITHIN_TARGET -> IncidentSloState.WITHIN_TARGET;
        case OVERDUE -> IncidentSloState.OVERDUE;
        case NOT_APPLICABLE -> IncidentSloState.NOT_APPLICABLE;
        };
    }
}

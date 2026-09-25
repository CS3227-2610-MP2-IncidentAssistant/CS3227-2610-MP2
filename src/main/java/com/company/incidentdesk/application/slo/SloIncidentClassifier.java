package com.company.incidentdesk.application.slo;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.application.presentation.SloSummaryModel;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.slo.SloCalculator;
import com.company.incidentdesk.domain.slo.SloComplianceState;
import com.company.incidentdesk.domain.slo.SloConfigurationHistory;
import com.company.incidentdesk.domain.slo.SloLiveMetricType;
import com.company.incidentdesk.domain.slo.SloStatusModel;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.persistence.IncidentSloClassifier;
import com.company.incidentdesk.persistence.IncidentSloState;
import com.company.incidentdesk.persistence.SloConfigurationStore;

/** Produces consistent live SLO filter states and presentation summaries. */
public final class SloIncidentClassifier implements IncidentSloClassifier {
    private final SloConfigurationStore store;
    private final Clock clock;

    public SloIncidentClassifier(SloConfigurationStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public IncidentSloState classify(Incident incident) {
        return switch (liveStatus(incident).state()) {
        case WITHIN_TARGET -> IncidentSloState.WITHIN_TARGET;
        case OVERDUE -> IncidentSloState.OVERDUE;
        case NOT_APPLICABLE -> IncidentSloState.NOT_APPLICABLE;
        };
    }

    public SloSummaryModel summarize(Incident incident) {
        Optional<SloTargetVersion> target = target(incident);
        SloStatusModel status = SloCalculator.liveStatus(incident, target, clock.instant());
        if (status.state() == SloComplianceState.NOT_APPLICABLE) {
            return new SloSummaryModel(target.isPresent() ? "Not applicable" : "Not configured", 0, false);
        }
        Duration elapsed = status.elapsed().orElseThrow();
        Duration limit = status.target().orElseThrow();
        String metric = status.metric().orElseThrow() == SloLiveMetricType.TIME_TO_CLAIM
                ? "Time to claim" : "Time in progress";
        boolean overdue = status.state() == SloComplianceState.OVERDUE;
        String label = (overdue ? "Overdue" : "Within target") + " · " + metric
                + " · " + elapsed.toMinutes() + "m / " + limit.toMinutes() + "m";
        return new SloSummaryModel(label, progress(elapsed, limit), overdue);
    }

    private SloStatusModel liveStatus(Incident incident) {
        return SloCalculator.liveStatus(incident, target(incident), clock.instant());
    }

    private Optional<SloTargetVersion> target(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        return new SloConfigurationHistory(store.findAll()).targetFor(incident.category(), clock.instant());
    }

    private static double progress(Duration elapsed, Duration target) {
        if (target.isZero()) {
            return elapsed.isZero() ? 0 : 1;
        }
        double elapsedSeconds = elapsed.getSeconds() + elapsed.getNano() / 1_000_000_000.0;
        double targetSeconds = target.getSeconds() + target.getNano() / 1_000_000_000.0;
        return Math.max(0, Math.min(1, elapsedSeconds / targetSeconds));
    }
}

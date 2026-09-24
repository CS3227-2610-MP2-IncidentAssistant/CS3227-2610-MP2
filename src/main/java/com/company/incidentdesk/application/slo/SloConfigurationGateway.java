package com.company.incidentdesk.application.slo;

import java.time.Duration;
import java.util.List;

import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.slo.SloTargetVersion;

/** Operations required by the administrator SLO configuration workflow. */
public interface SloConfigurationGateway {
    ApplicationResult<SloTargetVersion> configure(
            IncidentCategory category,
            Duration timeToClaimTarget,
            Duration timeInProgressTarget,
            double reopenRateTarget);

    ApplicationResult<List<SloTargetVersion>> currentTargets();

    ApplicationResult<List<SloTargetVersion>> history(IncidentCategory category);
}

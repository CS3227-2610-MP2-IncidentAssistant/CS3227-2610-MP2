package com.company.incidentdesk.persistence;

import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.domain.slo.SloTargetVersionId;

/** SLO configuration version history with atomic audited version commits. */
public interface SloConfigurationStore
        extends SloConfigurationRepository<SloTargetVersionId, SloTargetVersion>,
                AuditedMutationStore<SloTargetVersion> {
}

package com.company.incidentdesk.domain.slo;

/** Compliance state of one incident's current lifecycle position against its configured target. */
public enum SloComplianceState {
    WITHIN_TARGET,
    OVERDUE,
    NOT_APPLICABLE
}

package com.company.incidentdesk.domain.audit;

/** Safe record types that may be referenced without copying sensitive text into an event. */
public enum AuditEvidenceType {
    RESOLUTION,
    COMMENT,
    ATTACHMENT,
    PROMOTION_REQUEST,
    SLO_CONFIGURATION,
    FAILURE
}

package com.company.incidentdesk.domain.audit;

/** Controlled fields permitted in structured audit change summaries. */
public enum AuditChangeField {
    INCIDENT_STATUS,
    ASSIGNEE_ID,
    CATEGORY,
    ANONYMOUS,
    RESOLUTION_REFERENCE,
    COMMENT_REFERENCE,
    ATTACHMENT_REFERENCE,
    ACCOUNT_STATUS,
    ROLE,
    RESPONDER_CATEGORIES,
    PROMOTION_STATUS,
    SLO_VERSION,
    DATA_SCHEMA_VERSION,
    RECOVERY_SOURCE,
    FAILURE_CODE
}

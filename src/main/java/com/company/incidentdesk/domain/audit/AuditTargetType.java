package com.company.incidentdesk.domain.audit;

/** Types of records or application resources targeted by audited operations. */
public enum AuditTargetType {
    INCIDENT,
    ACCOUNT,
    PROMOTION_REQUEST,
    ATTACHMENT,
    SLO_CONFIGURATION,
    APPLICATION_DATA,
    AUTHENTICATION
}

package com.company.incidentdesk.persistence;

/** Incident fields supported by the base repository query contract. */
public enum IncidentSortField {
    CREATED_AT,
    SUBMITTED_AT,
    QUEUE_ENTERED_AT,
    TITLE,
    STATUS,
    CATEGORY
}

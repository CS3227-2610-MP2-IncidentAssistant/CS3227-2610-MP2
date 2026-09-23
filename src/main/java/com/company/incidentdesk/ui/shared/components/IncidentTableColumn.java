package com.company.incidentdesk.ui.shared.components;

/** Columns that role pages may opt into without forking the incident table. */
public enum IncidentTableColumn {
    REFERENCE,
    TITLE,
    CATEGORY,
    STATUS,
    SUBMITTED,
    REPORTER,
    ASSIGNEE,
    QUEUE_ENTERED,
    SLO
}

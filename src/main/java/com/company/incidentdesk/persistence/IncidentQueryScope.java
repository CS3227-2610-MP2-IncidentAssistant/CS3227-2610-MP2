package com.company.incidentdesk.persistence;

/** Supported storage query scopes; application authorization remains mandatory. */
public enum IncidentQueryScope {
    REPORTER_OWNED,
    RESPONDER_ELIGIBLE_UNASSIGNED,
    RESPONDER_ASSIGNED,
    ADMINISTRATOR_ALL
}

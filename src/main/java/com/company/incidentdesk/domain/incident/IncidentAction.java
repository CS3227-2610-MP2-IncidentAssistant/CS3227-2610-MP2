package com.company.incidentdesk.domain.incident;

/** Named operations that can create or change an incident lifecycle state. */
public enum IncidentAction {
    SAVE_DRAFT,
    SUBMIT,
    EDIT,
    WITHDRAW,
    CLAIM,
    ASSIGN,
    REASSIGN,
    RESOLVE,
    HANDOFF,
    REOPEN
}

package com.company.incidentdesk.application.presentation;

/** UI action visibility derived from, but never replacing, authorization policy decisions. */
public record IncidentActionModel(
        boolean edit,
        boolean withdraw,
        boolean claim,
        boolean resolve,
        boolean handoff,
        boolean reassign,
        boolean reopen,
        boolean comment,
        boolean attachmentAccess) {
}

package com.company.incidentdesk.application.presentation;

import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.persistence.IncidentSloState;

/** Central display labels for incident categories and lifecycle states. */
public final class IncidentDisplayLabels {
    private IncidentDisplayLabels() {
    }

    /** Returns the user-facing category label. */
    public static String category(IncidentCategory category) {
        return category.displayName();
    }

    /** Returns the user-facing status label. */
    public static String status(IncidentStatus status) {
        return switch (status) {
        case DRAFT -> "Draft";
        case SUBMITTED -> "Submitted";
        case ASSIGNED -> "Assigned";
        case RESOLVED -> "Resolved";
        case WITHDRAWN -> "Withdrawn";
        };
    }

    /** Returns the user-facing SLO state label. */
    public static String sloState(IncidentSloState sloState) {
        return switch (sloState) {
        case WITHIN_TARGET -> "Within target";
        case OVERDUE -> "Overdue";
        case NOT_APPLICABLE -> "Not applicable";
        };
    }
}

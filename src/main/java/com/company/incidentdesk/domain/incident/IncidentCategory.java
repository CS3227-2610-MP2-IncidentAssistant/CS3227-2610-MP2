package com.company.incidentdesk.domain.incident;

/** Categories available for classifying incidents. */
public enum IncidentCategory {
    IT("IT"),
    HUMAN_RELATIONS("Human Relations"),
    FACILITIES("Facilities");

    private final String displayName;

    IncidentCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

package com.company.incidentdesk.ui.navigation;

/** Destinations hosted by the authenticated application shell. */
public enum ApplicationRoute {
    DASHBOARD("Dashboard")
    ;

    private final String label;

    ApplicationRoute(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

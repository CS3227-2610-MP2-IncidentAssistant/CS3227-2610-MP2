package com.company.incidentdesk.ui.navigation;

import com.company.incidentdesk.domain.account.Role;

/** Destinations hosted by the authenticated application shell. */
public enum ApplicationRoute {
    DASHBOARD("Dashboard", false),
    ADMIN_ACCOUNTS("Accounts", true),
    ADMIN_SLO("SLO configuration", true)
    ;

    private final String label;
    private final boolean administratorOnly;

    ApplicationRoute(String label, boolean administratorOnly) {
        this.label = label;
        this.administratorOnly = administratorOnly;
    }

    public String label() {
        return label;
    }

    public boolean isAvailableTo(Role role) {
        return !administratorOnly || role == Role.ADMINISTRATOR;
    }

    public boolean isAdministratorOnly() {
        return administratorOnly;
    }
}

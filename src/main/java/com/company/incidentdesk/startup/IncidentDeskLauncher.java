package com.company.incidentdesk.startup;

import javafx.application.Application;

/** Verifies the runtime and launches Incident Desk. */
public final class IncidentDeskLauncher {
    private IncidentDeskLauncher() {
    }

    static void main(String[] args) {
        JavaVersionRequirement.verify(Runtime.version());
        Application.launch(IncidentDeskApplication.class, args);
    }
}

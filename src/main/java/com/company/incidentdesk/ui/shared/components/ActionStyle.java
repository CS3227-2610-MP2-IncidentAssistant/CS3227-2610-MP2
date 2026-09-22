package com.company.incidentdesk.ui.shared.components;

/** Supported visual emphasis for application actions. */
public enum ActionStyle {
    PRIMARY("primary"),
    SECONDARY("secondary"),
    DANGER("danger"),
    GHOST("ghost"),
    SMALL("small");

    private final String styleClass;

    ActionStyle(String styleClass) {
        this.styleClass = styleClass;
    }

    String styleClass() {
        return styleClass;
    }
}

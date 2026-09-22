package com.company.incidentdesk.ui.shared.components;

/** Presentation states for asynchronous application feedback. */
public enum FeedbackType {
    LOADING("loading-state"),
    EMPTY("empty-state"),
    ERROR("error-banner"),
    SUCCESS("toast");

    private final String styleClass;

    FeedbackType(String styleClass) {
        this.styleClass = styleClass;
    }

    String styleClass() {
        return styleClass;
    }
}

package com.company.incidentdesk.ui.shared.components;

/** Semantic visual tones that always accompany visible text. */
public enum SemanticTone {
    INFO("info"),
    SUCCESS("success"),
    WARNING("warning"),
    DANGER("danger"),
    NEUTRAL("neutral");

    private final String styleClass;

    SemanticTone(String styleClass) {
        this.styleClass = styleClass;
    }

    String styleClass() {
        return styleClass;
    }
}

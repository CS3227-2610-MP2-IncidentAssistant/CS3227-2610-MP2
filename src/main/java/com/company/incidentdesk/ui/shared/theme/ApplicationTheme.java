package com.company.incidentdesk.ui.shared.theme;

import java.net.URL;

import javafx.scene.Scene;

/** Installs the shared Incident Desk stylesheet on a scene. */
public final class ApplicationTheme {
    private static final String STYLESHEET = "application.css";

    private ApplicationTheme() {
    }

    /** Applies the application theme exactly once. */
    public static void applyTo(Scene scene) {
        URL stylesheet = ApplicationTheme.class.getResource(STYLESHEET);
        if (stylesheet == null) {
            throw new IllegalStateException("Missing application stylesheet: " + STYLESHEET);
        }

        String externalForm = stylesheet.toExternalForm();
        if (!scene.getStylesheets().contains(externalForm)) {
            scene.getStylesheets().add(externalForm);
        }
    }
}

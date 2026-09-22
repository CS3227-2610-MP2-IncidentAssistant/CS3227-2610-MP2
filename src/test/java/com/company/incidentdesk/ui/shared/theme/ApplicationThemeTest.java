package com.company.incidentdesk.ui.shared.theme;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ApplicationThemeTest {
    @Test
    void packagesTheSharedStylesheetWithRequiredVisualStates() throws IOException {
        URL stylesheet = ApplicationTheme.class.getResource("application.css");
        assertNotNull(stylesheet);

        String css;
        try (InputStream input = stylesheet.openStream()) {
            css = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(css.contains(".button.primary"));
        assertTrue(css.contains(".button.danger"));
        assertTrue(css.contains(".badge.success"));
        assertTrue(css.contains(".field-error"));
        assertTrue(css.contains(".empty-state"));
        assertTrue(css.contains(".error-banner"));
        assertTrue(css.contains(".toast"));
    }
}

package com.company.incidentdesk.ui.shared.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SloProgressBarTest {
    @Test
    void exposesAndUpdatesProgress() {
        SloProgressBar progressBar = new SloProgressBar(0.25, "Quarter elapsed");

        assertEquals(0.25, progressBar.getProgress());
        progressBar.setProgress(0.72);
        assertEquals(0.72, progressBar.getProgress());
    }

    @Test
    void rejectsProgressOutsideTheSupportedRange() {
        SloProgressBar progressBar = new SloProgressBar(0, "Not started");

        assertThrows(IllegalArgumentException.class, () -> progressBar.setProgress(-0.01));
        assertThrows(IllegalArgumentException.class, () -> progressBar.setProgress(1.01));
        assertThrows(IllegalArgumentException.class, () -> progressBar.setProgress(Double.NaN));
    }
}

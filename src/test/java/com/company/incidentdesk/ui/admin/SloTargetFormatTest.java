package com.company.incidentdesk.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SloTargetFormatTest {
    @Test
    void formatsDurationsAndPercentagesConsistently() {
        assertEquals("1h 30m", SloTargetFormat.duration(Duration.ofMinutes(90)));
        assertEquals("7%", SloTargetFormat.percent(0.07000000000001));
        assertEquals("7.5%", SloTargetFormat.percent(0.075));
        assertEquals("7.5", SloTargetFormat.percentValue(0.075));
    }
}

package com.company.incidentdesk.ui.shared.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class UiDateTimeFormatterTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");
    private static final ZonedDateTime REFERENCE = ZonedDateTime.of(2026, 9, 22, 10, 0, 0, 0, ZONE);

    @Test
    void describesTodayAndYesterday() {
        assertEquals("Today, 08:43", format(2026, 9, 22, 8, 43));
        assertEquals("Yesterday, 16:14", format(2026, 9, 21, 16, 14));
    }

    @Test
    void usesWeekdayWithinThePreviousWeek() {
        assertEquals("Friday, 12:00", format(2026, 9, 18, 12, 0));
    }

    @Test
    void usesCalendarDateForOlderComments() {
        assertEquals("24 June, 15:00", format(2026, 6, 24, 15, 0));
    }

    @Test
    void weekdayWindowIncludesLastSaturdayButNotThePreviousFriday() {
        ZonedDateTime friday = ZonedDateTime.of(2026, 9, 25, 18, 0, 0, 0, ZONE);
        ZonedDateTime lastSaturday = ZonedDateTime.of(2026, 9, 19, 12, 0, 0, 0, ZONE);
        ZonedDateTime previousFriday = ZonedDateTime.of(2026, 9, 18, 21, 5, 0, 0, ZONE);

        assertEquals("Saturday, 12:00", UiDateTimeFormatter.formatRelative(lastSaturday.toInstant(), friday));
        assertEquals("18 September, 21:05", UiDateTimeFormatter.formatRelative(previousFriday.toInstant(), friday));
    }

    private String format(int year, int month, int day, int hour, int minute) {
        ZonedDateTime timestamp = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE);
        return UiDateTimeFormatter.formatRelative(timestamp.toInstant(), REFERENCE);
    }
}

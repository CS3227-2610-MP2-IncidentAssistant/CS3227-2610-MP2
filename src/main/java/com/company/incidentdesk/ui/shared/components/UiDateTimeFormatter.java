package com.company.incidentdesk.ui.shared.components;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

/** Formats comment and timeline timestamps in concise local language. */
public final class UiDateTimeFormatter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);

    private UiDateTimeFormatter() {
    }

    public static String formatRelative(Instant timestamp, ZonedDateTime reference) {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(reference, "reference");

        ZonedDateTime localTimestamp = timestamp.atZone(reference.getZone());
        LocalDate eventDate = localTimestamp.toLocalDate();
        LocalDate referenceDate = reference.toLocalDate();
        String dayDescription = describeDay(eventDate, referenceDate);
        return dayDescription + ", " + TIME.format(localTimestamp);
    }

    private static String describeDay(LocalDate eventDate, LocalDate referenceDate) {
        if (eventDate.equals(referenceDate)) {
            return "Today";
        }
        if (eventDate.equals(referenceDate.minusDays(1))) {
            return "Yesterday";
        }

        long daysAgo = ChronoUnit.DAYS.between(eventDate, referenceDate);
        if (daysAgo > 1 && daysAgo < 7) {
            return DAY.format(eventDate);
        }
        return DATE.format(eventDate);
    }
}

package com.company.incidentdesk.ui.admin;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.Locale;

/** Consistent human-readable SLO target values across administrator views. */
final class SloTargetFormat {
    private SloTargetFormat() { }

    static String duration(Duration duration) {
        return duration.toHours() + "h " + duration.toMinutesPart() + "m";
    }

    static String percent(double rate) {
        return percentValue(rate) + "%";
    }

    static String percentValue(double rate) {
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(rate * 100);
    }
}

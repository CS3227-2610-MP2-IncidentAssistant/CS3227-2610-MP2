package com.company.incidentdesk.application.statistics;

/** Unit of a {@link MetricSeriesPoint#value()}. */
public enum MetricUnit {
    /** A whole-number tally. */
    COUNT,
    /** An elapsed duration expressed in seconds. */
    SECONDS,
    /** A fraction from 0 to 1. */
    RATIO
}

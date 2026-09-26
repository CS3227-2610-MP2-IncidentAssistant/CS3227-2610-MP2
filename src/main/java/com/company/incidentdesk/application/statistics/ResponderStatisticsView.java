package com.company.incidentdesk.application.statistics;

import java.util.Objects;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.statistics.ResponderStatistics;

/** Privacy-safe presentation of one responder's statistics, paired with a display-safe name. */
public record ResponderStatisticsView(AccountId responderId, String displayName, ResponderStatistics statistics) {
    public ResponderStatisticsView {
        Objects.requireNonNull(responderId, "responderId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(statistics, "statistics");
        if (!responderId.equals(statistics.responderId())) {
            throw new IllegalArgumentException("responderId must match statistics.responderId()");
        }
    }
}

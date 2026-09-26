package com.company.incidentdesk.application.statistics;

import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.account.AccountId;

/** Operations required by responder-performance and administrator-statistics dashboards. */
public interface StatisticsGateway {
    /** Returns one responder's own performance statistics; allowed for that responder or an administrator. */
    ApplicationResult<StatisticsResult> responderPerformance(AccountId responderId, StatisticsQuery query);

    /** Returns company-wide statistics; administrator only. */
    ApplicationResult<StatisticsResult> companyStatistics(StatisticsQuery query, boolean groupByResponder);
}

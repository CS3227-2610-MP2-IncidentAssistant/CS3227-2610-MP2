package com.company.incidentdesk.application.account;

import java.util.Objects;

import com.company.incidentdesk.application.event.ApplicationEvent;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.PromotionRequestId;

/** Post-commit promotion-request fact used for administrator notifications. */
public record PromotionRequestedEvent(PromotionRequestId requestId, AccountId requesterId)
        implements ApplicationEvent {
    public PromotionRequestedEvent {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requesterId, "requesterId");
    }
}

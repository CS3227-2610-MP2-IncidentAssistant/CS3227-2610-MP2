package com.company.incidentdesk.application.account;

import java.util.Objects;

import com.company.incidentdesk.application.event.ApplicationEvent;
import com.company.incidentdesk.domain.account.AccountId;

/** Post-commit responder-promotion decision fact. */
public record PromotionDecisionEvent(
        AccountId accountId,
        AccountId actorId,
        boolean approved) implements ApplicationEvent {
    public PromotionDecisionEvent {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(actorId, "actorId");
    }
}

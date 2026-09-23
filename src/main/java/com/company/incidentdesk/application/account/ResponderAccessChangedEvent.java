package com.company.incidentdesk.application.account;

import java.util.Objects;

import com.company.incidentdesk.application.event.ApplicationEvent;
import com.company.incidentdesk.domain.account.AccountId;

/** Post-commit responder-category access change fact. */
public record ResponderAccessChangedEvent(AccountId accountId, AccountId actorId) implements ApplicationEvent {
    public ResponderAccessChangedEvent {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(actorId, "actorId");
    }
}

package com.company.incidentdesk.application.account;

import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.ResponderPromotionRequest;

/** Atomic next state for a promotion request and optional account authorization change. */
public record PromotionWorkflowMutation(
        Optional<ResponderPromotionRequest> request,
        Optional<Account> updatedAccount) {
    public PromotionWorkflowMutation {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(updatedAccount, "updatedAccount");
    }
}

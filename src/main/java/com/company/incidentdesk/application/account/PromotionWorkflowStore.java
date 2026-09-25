package com.company.incidentdesk.application.account;

import java.util.List;
import java.util.Optional;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.PromotionRequestId;
import com.company.incidentdesk.domain.account.ResponderPromotionRequest;
import com.company.incidentdesk.persistence.AuditedMutation;

/** Persistence boundary for promotion history and atomic account authorization updates. */
public interface PromotionWorkflowStore {
    Optional<ResponderPromotionRequest> findPromotionRequest(PromotionRequestId requestId);

    List<ResponderPromotionRequest> findPromotionRequests();

    boolean hasPendingPromotionRequest(AccountId requesterId);

    void commitPromotion(AuditedMutation<PromotionWorkflowMutation> mutation);
}

package com.company.incidentdesk.domain.account;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for a responder-promotion request. */
public record PromotionRequestId(UUID value) {
    public PromotionRequestId {
        Objects.requireNonNull(value, "value");
    }
}

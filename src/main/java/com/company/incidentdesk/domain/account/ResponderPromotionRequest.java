package com.company.incidentdesk.domain.account;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.company.incidentdesk.domain.incident.IncidentCategory;

/** Immutable, retained history of a reporter's responder-promotion request. */
public record ResponderPromotionRequest(
        PromotionRequestId id,
        AccountId requesterId,
        Set<IncidentCategory> requestedCategories,
        Optional<String> comments,
        Instant requestedAt,
        PromotionRequestStatus status,
        Optional<AccountId> decidedBy,
        Optional<Instant> decidedAt) {
    public ResponderPromotionRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(requestedCategories, "requestedCategories");
        Objects.requireNonNull(comments, "comments");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(decidedBy, "decidedBy");
        Objects.requireNonNull(decidedAt, "decidedAt");
        if (requestedCategories.isEmpty()) {
            throw new IllegalArgumentException("requestedCategories must not be empty");
        }
        requestedCategories = Set.copyOf(EnumSet.copyOf(requestedCategories));
        comments = comments.map(String::trim).filter(value -> !value.isEmpty());
        if (status == PromotionRequestStatus.PENDING && (decidedBy.isPresent() || decidedAt.isPresent())) {
            throw new IllegalArgumentException("pending request cannot contain decision details");
        }
        if (status != PromotionRequestStatus.PENDING && (decidedBy.isEmpty() || decidedAt.isEmpty())) {
            throw new IllegalArgumentException("decided request requires decision details");
        }
    }

    public static ResponderPromotionRequest pending(PromotionRequestId id, AccountId requesterId,
            Set<IncidentCategory> categories, Optional<String> comments, Instant requestedAt) {
        return new ResponderPromotionRequest(id, requesterId, categories, comments, requestedAt,
                PromotionRequestStatus.PENDING, Optional.empty(), Optional.empty());
    }

    public ResponderPromotionRequest decide(boolean approved, AccountId administratorId, Instant decidedAt) {
        if (status != PromotionRequestStatus.PENDING) {
            throw new IllegalStateException("promotion request has already been decided");
        }
        return new ResponderPromotionRequest(id, requesterId, requestedCategories, comments, requestedAt,
                approved ? PromotionRequestStatus.APPROVED : PromotionRequestStatus.REJECTED,
                Optional.of(administratorId), Optional.of(decidedAt));
    }
}

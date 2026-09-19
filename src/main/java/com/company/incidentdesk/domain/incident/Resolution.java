package com.company.incidentdesk.domain.incident;

import java.time.Instant;
import java.util.Objects;

import com.company.incidentdesk.domain.account.AccountId;

/** Immutable result of completing one incident resolution cycle. */
public record Resolution(
        String remarks,
        Instant resolvedAt,
        AccountId resolvedBy,
        AccountId responderAtResolution) {
    /**
     * Creates a resolution while preserving the entered remarks.
     *
     * @param remarks non-blank resolution remarks
     * @param resolvedAt application-generated UTC resolution time
     * @param resolvedBy account that performed the resolution
     * @param responderAtResolution responder assigned when resolution occurred
     */
    public Resolution {
        Objects.requireNonNull(remarks, "remarks");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
        Objects.requireNonNull(resolvedBy, "resolvedBy");
        Objects.requireNonNull(responderAtResolution, "responderAtResolution");

        if (remarks.isBlank()) {
            throw new IllegalArgumentException("remarks must not be blank");
        }
    }
}

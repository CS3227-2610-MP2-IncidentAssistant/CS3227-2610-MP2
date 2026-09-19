package com.company.incidentdesk.domain.account;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for an application account. */
public record AccountId(UUID value) {
    /**
     * Creates an account identifier.
     *
     * @param value identifier value
     */
    public AccountId {
        Objects.requireNonNull(value, "value");
    }
}

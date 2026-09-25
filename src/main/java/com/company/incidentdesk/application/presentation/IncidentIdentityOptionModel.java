package com.company.incidentdesk.application.presentation;

import java.util.Objects;

import com.company.incidentdesk.domain.account.AccountId;

/** Authorized identity value exposed by an incident-list filter. */
public record IncidentIdentityOptionModel(AccountId id, String displayName) {
    public IncidentIdentityOptionModel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
    }
}

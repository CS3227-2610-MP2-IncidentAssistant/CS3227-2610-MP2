package com.company.incidentdesk.domain.account;

import java.util.Objects;

/** Immutable account identity and its current authorization attributes. */
public record Account(
        AccountId id,
        String loginName,
        Role role,
        AccountStatus status,
        ResponderAccess responderAccess) {
    /**
     * Creates an account and enforces role/access invariants.
     *
     * <p>Login names are preserved exactly because authentication is
     * case-sensitive.</p>
     *
     * @param id stable account identifier
     * @param loginName exact login name
     * @param role current application role
     * @param status enabled or disabled state
     * @param responderAccess assigned responder categories
     */
    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(loginName, "loginName");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(responderAccess, "responderAccess");

        if (loginName.isBlank()) {
            throw new IllegalArgumentException("loginName must not be blank");
        }
        if (role != Role.RESPONDER && !responderAccess.isEmpty()) {
            throw new IllegalArgumentException("Only responders may have category access");
        }
    }

    /**
     * Checks whether the account is currently enabled.
     *
     * @return true when the account may authenticate and act
     */
    public boolean isEnabled() {
        return status == AccountStatus.ENABLED;
    }

    /** Returns whether this identity is a non-login historical tombstone. */
    public boolean isDeleted() {
        return status == AccountStatus.DELETED;
    }

    /** Replaces login-capable identity data while retaining the stable account identifier. */
    public Account tombstone() {
        return new Account(id, "deleted:" + id.value(), role, AccountStatus.DELETED, ResponderAccess.NONE);
    }
}

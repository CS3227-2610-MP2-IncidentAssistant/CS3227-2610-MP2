package com.company.incidentdesk.application.account;

import java.util.Arrays;
import java.util.Optional;

/** Reset outcome whose plaintext credential can be consumed exactly once in memory. */
public final class TemporaryPasswordResetResult {
    public enum Status { RESET, RESOURCE_UNAVAILABLE, INVALID_STATE, FAILED }

    private final Status status;
    private char[] temporaryPassword;

    private TemporaryPasswordResetResult(Status status, char[] temporaryPassword) {
        this.status = status;
        this.temporaryPassword = temporaryPassword;
    }

    public static TemporaryPasswordResetResult reset(char[] password) {
        return new TemporaryPasswordResetResult(Status.RESET, Arrays.copyOf(password, password.length));
    }

    public static TemporaryPasswordResetResult failure(Status status) {
        if (status == Status.RESET) throw new IllegalArgumentException("reset requires a password");
        return new TemporaryPasswordResetResult(status, null);
    }

    public Status status() { return status; }

    public synchronized Optional<char[]> takeTemporaryPassword() {
        if (temporaryPassword == null) return Optional.empty();
        char[] revealed = temporaryPassword;
        temporaryPassword = null;
        return Optional.of(revealed);
    }
}

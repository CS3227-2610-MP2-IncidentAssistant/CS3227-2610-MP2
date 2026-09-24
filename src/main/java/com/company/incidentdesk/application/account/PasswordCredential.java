package com.company.incidentdesk.application.account;

import java.util.Arrays;
import java.util.Objects;
import java.time.Instant;
import java.util.Optional;

/** Persistable password derivation parameters and result; never contains plaintext. */
public record PasswordCredential(
        String algorithm, int iterations, byte[] salt, byte[] hash,
        boolean temporary, Optional<Instant> expiresAt) {
    public PasswordCredential(String algorithm, int iterations, byte[] salt, byte[] hash) {
        this(algorithm, iterations, salt, hash, false, Optional.empty());
    }

    public PasswordCredential {
        Objects.requireNonNull(algorithm, "algorithm");
        if (algorithm.isBlank() || iterations <= 0) {
            throw new IllegalArgumentException("invalid password derivation parameters");
        }
        salt = Arrays.copyOf(Objects.requireNonNull(salt, "salt"), salt.length);
        hash = Arrays.copyOf(Objects.requireNonNull(hash, "hash"), hash.length);
        if (salt.length < 16 || hash.length < 32) {
            throw new IllegalArgumentException("password credential is too short");
        }
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (temporary != expiresAt.isPresent()) {
            throw new IllegalArgumentException("only temporary credentials have an expiry");
        }
    }

    @Override
    public byte[] salt() {
        return Arrays.copyOf(salt, salt.length);
    }

    @Override
    public byte[] hash() {
        return Arrays.copyOf(hash, hash.length);
    }

    public PasswordCredential asTemporaryUntil(Instant expiry) {
        return new PasswordCredential(algorithm, iterations, salt, hash, true,
                Optional.of(Objects.requireNonNull(expiry, "expiry")));
    }
}

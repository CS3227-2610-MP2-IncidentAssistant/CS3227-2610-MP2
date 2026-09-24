package com.company.incidentdesk.application.account;

import java.util.Arrays;
import java.util.Objects;

/** Persistable password derivation parameters and result; never contains plaintext. */
public record PasswordCredential(String algorithm, int iterations, byte[] salt, byte[] hash) {
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
    }

    @Override
    public byte[] salt() {
        return Arrays.copyOf(salt, salt.length);
    }

    @Override
    public byte[] hash() {
        return Arrays.copyOf(hash, hash.length);
    }
}

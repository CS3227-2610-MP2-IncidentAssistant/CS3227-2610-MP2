package com.company.incidentdesk.application.account;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActor;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditChange;
import com.company.incidentdesk.domain.audit.AuditChangeField;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** Registers accounts and verifies salted PBKDF2 password credentials. */
public final class AccountRegistrationService implements PasswordVerifier, AccountRegistrar {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private final AccountRegistrationStore store;
    private final Clock clock;
    private final SecureRandom random;

    public AccountRegistrationService(AccountRegistrationStore store, Clock clock) {
        this(store, clock, new SecureRandom());
    }

    AccountRegistrationService(AccountRegistrationStore store, Clock clock, SecureRandom random) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public RegistrationResult register(String loginName, char[] password, Role role) {
        Objects.requireNonNull(loginName, "loginName");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(role, "role");
        if (loginName.isBlank()) {
            return RegistrationResult.INVALID_LOGIN_NAME;
        }
        if (password.length == 0) {
            return RegistrationResult.INVALID_PASSWORD;
        }
        char[] temporary = Arrays.copyOf(password, password.length);
        try {
            AccountId id = new AccountId(UUID.randomUUID());
            Account account = new Account(id, loginName, role, AccountStatus.ENABLED, ResponderAccess.NONE);
            PasswordCredential credential = hash(temporary);
            AuditEvent audit = new AuditEvent(new AuditEventId(UUID.randomUUID()), clock.instant(),
                    new AuditActor(id, role, AuditActorVisibility.STANDARD), AuditAction.ACCOUNT_REGISTERED,
                    new AuditTarget(AuditTargetType.ACCOUNT, id.value().toString()), AuditOutcome.SUCCESS,
                    List.of(new AuditChange(AuditChangeField.ROLE, Optional.empty(), Optional.of(role.name()))),
                    Optional.empty());
            store.register(new AccountRegistration(account, credential, audit));
            return RegistrationResult.REGISTERED;
        } catch (RepositoryException exception) {
            return exception.code() == StorageFailureCode.ALREADY_EXISTS
                    ? RegistrationResult.DUPLICATE_LOGIN_NAME : RegistrationResult.FAILED;
        } finally {
            Arrays.fill(temporary, '\0');
        }
    }

    @Override
    public boolean verify(AccountId accountId, char[] candidatePassword) {
        return store.findCredential(accountId).map(credential -> {
            if (credential.temporary() && !clock.instant().isBefore(credential.expiresAt().orElseThrow())) {
                return false;
            }
            byte[] candidate = derive(candidatePassword, credential.salt(), credential.iterations());
            try {
                return MessageDigest.isEqual(candidate, credential.hash());
            } finally {
                Arrays.fill(candidate, (byte) 0);
            }
        }).orElse(false);
    }

    public boolean isTemporaryAndValid(AccountId accountId) {
        return store.findCredential(accountId).filter(PasswordCredential::temporary)
                .filter(value -> clock.instant().isBefore(value.expiresAt().orElseThrow())).isPresent();
    }

    PasswordCredential hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new PasswordCredential(ALGORITHM, ITERATIONS, salt, derive(password, salt, ITERATIONS));
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        } finally {
            spec.clearPassword();
        }
    }
}

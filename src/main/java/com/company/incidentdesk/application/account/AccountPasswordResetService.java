package com.company.incidentdesk.application.account;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.session.SessionService;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.persistence.RepositoryException;

/** Administrator-only, recovery-safe temporary-password reset. */
public final class AccountPasswordResetService {
    public static final Duration TEMPORARY_PASSWORD_LIFETIME = Duration.ofHours(24);
    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*?".toCharArray();
    private static final int PASSWORD_LENGTH = 24;

    private final SessionService sessions;
    private final AccountAuthorizationPolicy authorization;
    private final AccountRegistrationService credentials;
    private final PasswordResetStore store;
    private final AuditEventFactory auditEvents;
    private final Clock clock;
    private final SecureRandom random;

    public AccountPasswordResetService(SessionService sessions, AccountAuthorizationPolicy authorization,
            AccountRegistrationService credentials, PasswordResetStore store, AuditEventFactory auditEvents,
            Clock clock) {
        this(sessions, authorization, credentials, store, auditEvents, clock, new SecureRandom());
    }

    AccountPasswordResetService(SessionService sessions, AccountAuthorizationPolicy authorization,
            AccountRegistrationService credentials, PasswordResetStore store, AuditEventFactory auditEvents,
            Clock clock, SecureRandom random) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.store = Objects.requireNonNull(store, "store");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public boolean canReset(AccountId targetId) {
        Optional<Account> actor = sessions.currentAccount();
        return authorization.authorizeDeleteOrResetAccount().isAllowed() && actor.isPresent()
                && !actor.orElseThrow().id().equals(targetId)
                && store.findById(targetId).filter(Account::isEnabled).isPresent();
    }

    public TemporaryPasswordResetResult reset(AccountId targetId) {
        Objects.requireNonNull(targetId, "targetId");
        Optional<Account> actor = sessions.currentAccount();
        if (!authorization.authorizeDeleteOrResetAccount().isAllowed() || actor.isEmpty()) {
            return TemporaryPasswordResetResult.failure(TemporaryPasswordResetResult.Status.RESOURCE_UNAVAILABLE);
        }
        if (actor.orElseThrow().id().equals(targetId)) {
            return TemporaryPasswordResetResult.failure(TemporaryPasswordResetResult.Status.INVALID_STATE);
        }
        Optional<Account> target = store.findById(targetId).filter(Account::isEnabled);
        if (target.isEmpty()) {
            return TemporaryPasswordResetResult.failure(TemporaryPasswordResetResult.Status.RESOURCE_UNAVAILABLE);
        }

        char[] plaintext = generatePassword();
        try {
            PasswordCredential credential = credentials.hash(plaintext)
                    .asTemporaryUntil(clock.instant().plus(TEMPORARY_PASSWORD_LIFETIME));
            var audit = auditEvents.create(actor.orElseThrow(), AuditActorVisibility.STANDARD,
                    AuditAction.PASSWORD_RESET_INITIATED,
                    new AuditTarget(AuditTargetType.ACCOUNT, targetId.value().toString()), AuditOutcome.SUCCESS,
                    List.of(), Optional.empty());
            store.resetPassword(targetId, credential, audit);
            sessions.invalidate(targetId);
            return TemporaryPasswordResetResult.reset(plaintext);
        } catch (RepositoryException exception) {
            return TemporaryPasswordResetResult.failure(TemporaryPasswordResetResult.Status.FAILED);
        } finally {
            Arrays.fill(plaintext, '\0');
        }
    }

    private char[] generatePassword() {
        char[] password = new char[PASSWORD_LENGTH];
        for (int index = 0; index < password.length; index++) {
            password[index] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return password;
    }
}

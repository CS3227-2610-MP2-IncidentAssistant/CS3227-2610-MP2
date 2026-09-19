package com.company.incidentdesk.application.session;

import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.company.incidentdesk.application.account.AccountLookup;
import com.company.incidentdesk.application.account.PasswordVerifier;
import com.company.incidentdesk.domain.account.Account;

/** Maintains exactly one process-local authenticated session. */
public final class InMemorySessionService implements SessionService {
    private final AccountLookup accountLookup;
    private final PasswordVerifier passwordVerifier;
    private final Clock clock;
    private final AtomicReference<AuthenticatedSession> activeSession = new AtomicReference<>();

    /**
     * Creates a session service.
     *
     * @param accountLookup current account source
     * @param passwordVerifier credential verifier
     * @param clock application clock used for UTC timestamps
     */
    public InMemorySessionService(
            AccountLookup accountLookup,
            PasswordVerifier passwordVerifier,
            Clock clock) {
        this.accountLookup = Objects.requireNonNull(accountLookup, "accountLookup");
        this.passwordVerifier = Objects.requireNonNull(passwordVerifier, "passwordVerifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AuthenticationResult login(String loginName, char[] password) {
        Objects.requireNonNull(loginName, "loginName");
        Objects.requireNonNull(password, "password");

        Optional<Account> locatedAccount = accountLookup.findByLoginName(loginName);
        if (locatedAccount.isEmpty()) {
            return AuthenticationResult.REJECTED;
        }

        Account enabledAccount = locatedAccount.orElseThrow();
        if (!enabledAccount.isEnabled()) {
            return AuthenticationResult.REJECTED;
        }

        char[] temporaryPassword = Arrays.copyOf(password, password.length);
        boolean verified;
        try {
            verified = passwordVerifier.verify(enabledAccount.id(), temporaryPassword);
        } finally {
            Arrays.fill(temporaryPassword, '\0');
        }
        if (!verified) {
            return AuthenticationResult.REJECTED;
        }

        AuthenticatedSession newSession = new AuthenticatedSession(enabledAccount.id(), clock.instant());
        activeSession.set(newSession);
        return AuthenticationResult.AUTHENTICATED;
    }

    @Override
    public void logout() {
        activeSession.set(null);
    }

    @Override
    public Optional<AuthenticatedSession> currentSession() {
        AuthenticatedSession session = activeSession.get();
        if (session == null) {
            return Optional.empty();
        }
        if (loadEnabledAccount(session).isEmpty()) {
            activeSession.compareAndSet(session, null);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public Optional<Account> currentAccount() {
        AuthenticatedSession session = activeSession.get();
        if (session == null) {
            return Optional.empty();
        }

        Optional<Account> account = loadEnabledAccount(session);
        if (account.isEmpty()) {
            activeSession.compareAndSet(session, null);
        }
        return account;
    }

    private Optional<Account> loadEnabledAccount(AuthenticatedSession session) {
        return accountLookup.findById(session.accountId()).filter(Account::isEnabled);
    }
}

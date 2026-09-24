package com.company.incidentdesk.application.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.account.AccountLookup;
import com.company.incidentdesk.application.account.PasswordVerifier;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentCategory;

/** Tests authentication and single-session behavior. */
class InMemorySessionServiceTest {
    private static final Instant AUTHENTICATED_AT = Instant.parse("2026-09-19T08:00:00Z");
    private static final char[] VALID_PASSWORD = "valid-password".toCharArray();
    private static final AccountId FIRST_ID = new AccountId(
            UUID.fromString("c2486a9d-48cb-4485-8762-eccfd46ae724"));
    private static final AccountId SECOND_ID = new AccountId(
            UUID.fromString("fb2ae05d-b964-4550-8499-13d26a26ff6b"));

    private MutableAccountLookup accountLookup;
    private RecordingPasswordVerifier passwordVerifier;
    private InMemorySessionService sessionService;

    @BeforeEach
    void setUp() {
        accountLookup = new MutableAccountLookup();
        passwordVerifier = new RecordingPasswordVerifier();
        sessionService = new InMemorySessionService(
                accountLookup,
                passwordVerifier,
                Clock.fixed(AUTHENTICATED_AT, ZoneOffset.UTC));
    }

    @Test
    void successfulLoginCreatesSession() {
        Account account = reporter(FIRST_ID, "Alice", AccountStatus.ENABLED);
        addValidAccount(account);

        AuthenticationResult result = sessionService.login("Alice", VALID_PASSWORD);

        assertEquals(AuthenticationResult.AUTHENTICATED, result);
        AuthenticatedSession session = sessionService.currentSession().orElseThrow();
        assertEquals(FIRST_ID, session.accountId());
        assertEquals(AUTHENTICATED_AT, session.authenticatedAt());
        assertEquals(account, sessionService.currentAccount().orElseThrow());
    }

    @Test
    void loginNamesAreCaseSensitiveAndCaseVariantsAreDistinct() {
        Account upperCaseAccount = reporter(FIRST_ID, "Alice", AccountStatus.ENABLED);
        Account lowerCaseAccount = reporter(SECOND_ID, "alice", AccountStatus.ENABLED);
        addValidAccount(upperCaseAccount);
        addValidAccount(lowerCaseAccount);

        assertEquals(AuthenticationResult.AUTHENTICATED, sessionService.login("Alice", VALID_PASSWORD));
        assertEquals(FIRST_ID, sessionService.currentSession().orElseThrow().accountId());

        assertEquals(AuthenticationResult.AUTHENTICATED, sessionService.login("alice", VALID_PASSWORD));
        assertEquals(SECOND_ID, sessionService.currentSession().orElseThrow().accountId());

        assertEquals(AuthenticationResult.REJECTED, sessionService.login("ALICE", VALID_PASSWORD));
        assertEquals(SECOND_ID, sessionService.currentSession().orElseThrow().accountId());
    }

    @Test
    void rejectedLoginPreservesExistingSession() {
        Account firstAccount = reporter(FIRST_ID, "first", AccountStatus.ENABLED);
        Account secondAccount = reporter(SECOND_ID, "second", AccountStatus.ENABLED);
        addValidAccount(firstAccount);
        accountLookup.put(secondAccount);

        assertEquals(AuthenticationResult.AUTHENTICATED, sessionService.login("first", VALID_PASSWORD));
        assertEquals(AuthenticationResult.REJECTED, sessionService.login("second", "wrong".toCharArray()));

        assertEquals(FIRST_ID, sessionService.currentSession().orElseThrow().accountId());
    }

    @Test
    void successfulLoginReplacesExistingSession() {
        Account firstAccount = reporter(FIRST_ID, "first", AccountStatus.ENABLED);
        Account secondAccount = reporter(SECOND_ID, "second", AccountStatus.ENABLED);
        addValidAccount(firstAccount);
        addValidAccount(secondAccount);

        sessionService.login("first", VALID_PASSWORD);
        sessionService.login("second", VALID_PASSWORD);

        assertEquals(SECOND_ID, sessionService.currentSession().orElseThrow().accountId());
    }

    @Test
    void disabledAccountCannotAuthenticate() {
        Account disabled = reporter(FIRST_ID, "disabled", AccountStatus.DISABLED);
        addValidAccount(disabled);

        assertEquals(AuthenticationResult.REJECTED, sessionService.login("disabled", VALID_PASSWORD));
        assertTrue(sessionService.currentSession().isEmpty());
    }

    @Test
    void deletedAccountCannotAuthenticateEvenWhenCredentialVerifierWouldAcceptIt() {
        Account deleted = reporter(FIRST_ID, "deleted:" + FIRST_ID.value(), AccountStatus.DELETED);
        addValidAccount(deleted);

        assertEquals(AuthenticationResult.REJECTED,
                sessionService.login(deleted.loginName(), VALID_PASSWORD));
        assertTrue(sessionService.currentSession().isEmpty());
    }

    @Test
    void disablingCurrentAccountInvalidatesSession() {
        Account enabled = reporter(FIRST_ID, "reporter", AccountStatus.ENABLED);
        addValidAccount(enabled);
        sessionService.login("reporter", VALID_PASSWORD);

        accountLookup.put(reporter(FIRST_ID, "reporter", AccountStatus.DISABLED));

        assertTrue(sessionService.currentAccount().isEmpty());
        assertTrue(sessionService.currentSession().isEmpty());
    }

    @Test
    void currentAccountReloadsRoleAndCategoryAccess() {
        Account reporter = reporter(FIRST_ID, "promoted", AccountStatus.ENABLED);
        addValidAccount(reporter);
        sessionService.login("promoted", VALID_PASSWORD);

        Account responder = new Account(
                FIRST_ID,
                "promoted",
                Role.RESPONDER,
                AccountStatus.ENABLED,
                ResponderAccess.to(Set.of(IncidentCategory.IT)));
        accountLookup.put(responder);

        Account refreshed = sessionService.currentAccount().orElseThrow();
        assertEquals(Role.RESPONDER, refreshed.role());
        assertTrue(refreshed.responderAccess().permits(IncidentCategory.IT));
    }

    @Test
    void logoutIsSafeWithAndWithoutActiveSession() {
        Account account = reporter(FIRST_ID, "reporter", AccountStatus.ENABLED);
        addValidAccount(account);
        sessionService.login("reporter", VALID_PASSWORD);

        sessionService.logout();
        sessionService.logout();

        assertTrue(sessionService.currentSession().isEmpty());
        assertTrue(sessionService.currentAccount().isEmpty());
    }

    @Test
    void temporaryPasswordCopyIsClearedAfterVerification() {
        Account account = reporter(FIRST_ID, "reporter", AccountStatus.ENABLED);
        addValidAccount(account);

        sessionService.login("reporter", VALID_PASSWORD);

        assertTrue(passwordVerifier.lastCandidateWasCleared());
        assertFalse(Arrays.equals(VALID_PASSWORD, passwordVerifier.lastCandidate));
    }

    private void addValidAccount(Account account) {
        accountLookup.put(account);
        passwordVerifier.allow(account.id());
    }

    private Account reporter(AccountId id, String loginName, AccountStatus status) {
        return new Account(id, loginName, Role.REPORTER, status, ResponderAccess.NONE);
    }

    /** Mutable account source used to verify permission refresh behavior. */
    private static final class MutableAccountLookup implements AccountLookup {
        private final Map<AccountId, Account> accountsById = new HashMap<>();
        private final Map<String, AccountId> accountIdsByLoginName = new HashMap<>();

        @Override
        public Optional<Account> findById(AccountId accountId) {
            return Optional.ofNullable(accountsById.get(accountId));
        }

        @Override
        public Optional<Account> findByLoginName(String loginName) {
            return Optional.ofNullable(accountIdsByLoginName.get(loginName)).flatMap(this::findById);
        }

        private void put(Account account) {
            accountsById.put(account.id(), account);
            accountIdsByLoginName.put(account.loginName(), account.id());
        }
    }

    /** Password verifier that accepts registered account identifiers. */
    private static final class RecordingPasswordVerifier implements PasswordVerifier {
        private final Set<AccountId> allowedAccounts = new java.util.HashSet<>();
        private char[] lastCandidate;

        @Override
        public boolean verify(AccountId accountId, char[] candidatePassword) {
            lastCandidate = candidatePassword;
            return allowedAccounts.contains(accountId) && Arrays.equals(VALID_PASSWORD, candidatePassword);
        }

        private void allow(AccountId accountId) {
            allowedAccounts.add(accountId);
        }

        private boolean lastCandidateWasCleared() {
            return lastCandidate != null && Arrays.equals(lastCandidate, new char[lastCandidate.length]);
        }
    }
}

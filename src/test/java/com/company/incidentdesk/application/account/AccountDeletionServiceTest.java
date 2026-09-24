package com.company.incidentdesk.application.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.AuthenticationResult;
import com.company.incidentdesk.application.session.SessionService;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

class AccountDeletionServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-24T02:00:00Z");
    private static final Account ADMIN = account(1, "admin", Role.ADMINISTRATOR);
    private static final Account REPORTER = account(2, "reporter", Role.REPORTER);
    private static final Account OTHER_REPORTER = account(3, "other-reporter", Role.REPORTER);

    @Test
    void administratorTombstonesAccountAndRepeatDeletionIsRejected() {
        TestSession sessions = new TestSession(ADMIN);
        TestStore store = new TestStore(ADMIN, REPORTER);
        AccountDeletionService service = service(sessions, store);

        assertTrue(service.delete(REPORTER.id()).isSuccess());
        Account tombstone = store.findById(REPORTER.id()).orElseThrow();
        assertTrue(tombstone.isDeleted());
        assertEquals("deleted:" + REPORTER.id().value(), tombstone.loginName());
        assertEquals(AuditAction.ACCOUNT_DELETED, store.audit.action());

        var repeated = service.delete(REPORTER.id());
        assertFalse(repeated.isSuccess());
        assertEquals(ApplicationErrorCode.INVALID_STATE, repeated.error().orElseThrow().code());
    }

    @Test
    void nonAdministratorCannotDeleteAccount() {
        TestSession sessions = new TestSession(REPORTER);
        TestStore store = new TestStore(ADMIN, REPORTER);

        var result = service(sessions, store).delete(REPORTER.id());

        assertFalse(result.isSuccess());
        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, result.error().orElseThrow().code());
        assertEquals(AccountStatus.ENABLED, store.findById(REPORTER.id()).orElseThrow().status());
    }

    @Test
    void deletingCurrentAdministratorIsRejectedAndSessionRemainsActive() {
        TestSession sessions = new TestSession(ADMIN);
        TestStore store = new TestStore(ADMIN);

        var result = service(sessions, store).delete(ADMIN.id());

        assertFalse(result.isSuccess());
        assertEquals(ApplicationErrorCode.INVALID_STATE, result.error().orElseThrow().code());
        assertTrue(sessions.currentAccount().isPresent());
        assertTrue(sessions.currentSession().isPresent());
        assertEquals(AccountStatus.ENABLED, store.findById(ADMIN.id()).orElseThrow().status());
    }

    @Test
    void deletionAvailabilityExcludesSelfDeletedAndMissingAccounts() {
        TestSession sessions = new TestSession(ADMIN);
        TestStore store = new TestStore(ADMIN, REPORTER.tombstone(), OTHER_REPORTER);
        AccountDeletionService service = service(sessions, store);

        assertFalse(service.canDelete(ADMIN.id()));
        assertFalse(service.canDelete(REPORTER.id()));
        assertFalse(service.canDelete(new AccountId(new UUID(0, 404))));
        assertTrue(service.canDelete(OTHER_REPORTER.id()));
    }

    @Test
    void persistenceFailureLeavesAccountAndSessionUnchanged() {
        TestSession sessions = new TestSession(ADMIN);
        TestStore store = new TestStore(ADMIN, REPORTER);
        store.fail = true;

        var result = service(sessions, store).delete(REPORTER.id());

        assertFalse(result.isSuccess());
        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, result.error().orElseThrow().code());
        assertEquals(AccountStatus.ENABLED, store.findById(REPORTER.id()).orElseThrow().status());
        assertTrue(sessions.currentAccount().isPresent());
    }

    private static AccountDeletionService service(TestSession sessions, TestStore store) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new AccountDeletionService(sessions, new AccountAuthorizationPolicy(sessions), store,
                new AuditEventFactory(clock, () -> new AuditEventId(new UUID(0, 99))));
    }

    private static Account account(long id, String name, Role role) {
        return new Account(new AccountId(new UUID(0, id)), name, role, AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    private static final class TestStore implements AccountDeletionStore {
        private final Map<AccountId, Account> accounts = new LinkedHashMap<>();
        private boolean fail;
        private AuditEvent audit;

        TestStore(Account... initialAccounts) {
            for (Account account : initialAccounts) {
                accounts.put(account.id(), account);
            }
        }

        @Override
        public Optional<Account> findById(AccountId accountId) {
            return Optional.ofNullable(accounts.get(accountId));
        }

        @Override
        public Optional<Account> findByLoginName(String loginName) {
            return accounts.values().stream().filter(account -> account.loginName().equals(loginName)).findFirst();
        }

        @Override
        public void delete(Account tombstone, AuditEvent auditEvent) {
            if (fail) {
                throw new RepositoryException(StorageFailureCode.STORAGE_UNAVAILABLE, "simulated");
            }
            accounts.put(tombstone.id(), tombstone);
            audit = auditEvent;
        }
    }

    private static final class TestSession implements SessionService {
        private Account account;

        TestSession(Account account) {
            this.account = account;
        }

        @Override
        public AuthenticationResult login(String loginName, char[] password) {
            return AuthenticationResult.REJECTED;
        }

        @Override
        public void logout() {
            account = null;
        }

        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return account == null ? Optional.empty() : Optional.of(new AuthenticatedSession(account.id(), NOW));
        }

        @Override
        public Optional<Account> currentAccount() {
            return Optional.ofNullable(account);
        }
    }
}

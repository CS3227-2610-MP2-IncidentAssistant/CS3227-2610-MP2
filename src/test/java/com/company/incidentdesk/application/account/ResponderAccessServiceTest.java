package com.company.incidentdesk.application.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.event.ApplicationEvent;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.persistence.AuditedMutation;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

class ResponderAccessServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-25T02:00:00Z");
    private static final AccountId ADMIN_ID = id(1);
    private static final AccountId REPORTER_ID = id(2);
    private static final AccountId RESPONDER_ID = id(3);

    @Test
    void administratorGrantsCategoriesToResponder() {
        TestAccounts accounts = new TestAccounts(admin(), reporter(),
                responder(Set.of(IncidentCategory.IT)));
        TestPromotionStore store = new TestPromotionStore(accounts);
        TestSession sessions = new TestSession(accounts, ADMIN_ID);
        ResponderAccessService service = service(sessions, accounts, store);

        var result = service.changeCategories(RESPONDER_ID,
                Set.of(IncidentCategory.IT, IncidentCategory.FACILITIES));

        assertTrue(result.isSuccess());
        assertEquals(Set.of(IncidentCategory.IT, IncidentCategory.FACILITIES),
                accounts.findById(RESPONDER_ID).orElseThrow().responderAccess().categories());
        assertEquals(AuditAction.RESPONDER_ACCESS_CHANGED, store.lastAudit.action());
    }

    @Test
    void administratorRevokesCategoriesFromResponder() {
        TestAccounts accounts = new TestAccounts(admin(), reporter(),
                responder(Set.of(IncidentCategory.IT, IncidentCategory.FACILITIES)));
        TestSession sessions = new TestSession(accounts, ADMIN_ID);
        ResponderAccessService service = service(sessions, accounts, new TestPromotionStore(accounts));

        var result = service.changeCategories(RESPONDER_ID, Set.of(IncidentCategory.IT));

        assertTrue(result.isSuccess());
        assertEquals(Set.of(IncidentCategory.IT),
                accounts.findById(RESPONDER_ID).orElseThrow().responderAccess().categories());
    }

    @Test
    void administratorClearsCategoriesToEmptySet() {
        TestAccounts accounts = new TestAccounts(admin(), reporter(),
                responder(Set.of(IncidentCategory.IT)));
        TestSession sessions = new TestSession(accounts, ADMIN_ID);
        ResponderAccessService service = service(sessions, accounts, new TestPromotionStore(accounts));

        var result = service.changeCategories(RESPONDER_ID, Set.of());

        assertTrue(result.isSuccess());
        assertTrue(accounts.findById(RESPONDER_ID).orElseThrow().responderAccess().isEmpty());
    }

    @Test
    void nonAdministratorCannotChangeCategoriesAndAccountIsUnchanged() {
        TestAccounts accounts = new TestAccounts(admin(), reporter(),
                responder(Set.of(IncidentCategory.IT)));
        TestSession sessions = new TestSession(accounts, REPORTER_ID);
        ResponderAccessService service = service(sessions, accounts, new TestPromotionStore(accounts));

        var result = service.changeCategories(RESPONDER_ID, Set.of(IncidentCategory.FACILITIES));

        assertFalse(result.isSuccess());
        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, result.error().orElseThrow().code());
        assertEquals(Set.of(IncidentCategory.IT),
                accounts.findById(RESPONDER_ID).orElseThrow().responderAccess().categories());
    }

    @Test
    void directInvocationAgainstNonResponderAccountIsDenied() {
        TestAccounts accounts = new TestAccounts(admin(), reporter());
        TestSession sessions = new TestSession(accounts, ADMIN_ID);
        ResponderAccessService service = service(sessions, accounts, new TestPromotionStore(accounts));

        var result = service.changeCategories(REPORTER_ID, Set.of(IncidentCategory.IT));

        assertFalse(result.isSuccess());
        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, result.error().orElseThrow().code());
    }

    @Test
    void storageFailureLeavesCategoriesUnchangedAndReportsPersistenceFailure() {
        TestAccounts accounts = new TestAccounts(admin(), reporter(),
                responder(Set.of(IncidentCategory.IT)));
        TestSession sessions = new TestSession(accounts, ADMIN_ID);
        TestPromotionStore store = new TestPromotionStore(accounts);
        store.fail = true;
        ResponderAccessService service = service(sessions, accounts, store);

        var result = service.changeCategories(RESPONDER_ID, Set.of(IncidentCategory.FACILITIES));

        assertFalse(result.isSuccess());
        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, result.error().orElseThrow().code());
        assertEquals(Set.of(IncidentCategory.IT),
                accounts.findById(RESPONDER_ID).orElseThrow().responderAccess().categories());
    }

    @Test
    void repeatSubmissionWithSameCategoriesSucceedsIdempotently() {
        TestAccounts accounts = new TestAccounts(admin(), reporter(),
                responder(Set.of(IncidentCategory.IT)));
        TestSession sessions = new TestSession(accounts, ADMIN_ID);
        TestPromotionStore store = new TestPromotionStore(accounts);
        ResponderAccessService service = service(sessions, accounts, store);

        assertTrue(service.changeCategories(RESPONDER_ID, Set.of(IncidentCategory.FACILITIES)).isSuccess());
        assertTrue(service.changeCategories(RESPONDER_ID, Set.of(IncidentCategory.FACILITIES)).isSuccess());

        assertEquals(Set.of(IncidentCategory.FACILITIES),
                accounts.findById(RESPONDER_ID).orElseThrow().responderAccess().categories());
        assertEquals(2, store.commitCount);
    }

    private static ResponderAccessService service(
            TestSession sessions, TestAccounts accounts, TestPromotionStore store) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        List<ApplicationEvent> events = new ArrayList<>();
        return new ResponderAccessService(sessions, new AccountAuthorizationPolicy(sessions), accounts, store,
                new AuditEventFactory(clock, () -> new AuditEventId(UUID.randomUUID())), events::add);
    }

    private static Account admin() {
        return new Account(ADMIN_ID, "admin", Role.ADMINISTRATOR, AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    private static Account reporter() {
        return new Account(REPORTER_ID, "reporter", Role.REPORTER, AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    private static Account responder(Set<IncidentCategory> categories) {
        return new Account(RESPONDER_ID, "responder", Role.RESPONDER, AccountStatus.ENABLED,
                ResponderAccess.to(categories));
    }

    private static AccountId id(long value) {
        return new AccountId(new UUID(0, value));
    }

    private static final class TestSession implements SessionProvider {
        private final TestAccounts accounts;
        private final AccountId accountId;

        private TestSession(TestAccounts accounts, AccountId accountId) {
            this.accounts = accounts;
            this.accountId = accountId;
        }

        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return Optional.of(new AuthenticatedSession(accountId, NOW));
        }

        @Override
        public Optional<Account> currentAccount() {
            return accounts.findById(accountId).filter(Account::isEnabled);
        }
    }

    private static final class TestAccounts implements com.company.incidentdesk.persistence.AccountRepository {
        private final Map<AccountId, Account> accounts = new LinkedHashMap<>();

        private TestAccounts(Account... initial) {
            for (Account account : initial) {
                accounts.put(account.id(), account);
            }
        }

        @Override
        public void create(Account account) {
            accounts.put(account.id(), account);
        }

        @Override
        public void update(Account account) {
            accounts.put(account.id(), account);
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
        public List<Account> findAll() {
            return List.copyOf(accounts.values());
        }
    }

    private static final class TestPromotionStore implements PromotionWorkflowStore {
        private final TestAccounts accounts;
        private boolean fail;
        private int commitCount;
        private AuditEvent lastAudit;

        private TestPromotionStore(TestAccounts accounts) {
            this.accounts = accounts;
        }

        @Override
        public Optional<com.company.incidentdesk.domain.account.ResponderPromotionRequest> findPromotionRequest(
                com.company.incidentdesk.domain.account.PromotionRequestId requestId) {
            return Optional.empty();
        }

        @Override
        public List<com.company.incidentdesk.domain.account.ResponderPromotionRequest> findPromotionRequests() {
            return List.of();
        }

        @Override
        public boolean hasPendingPromotionRequest(AccountId requesterId) {
            return false;
        }

        @Override
        public void commitPromotion(AuditedMutation<PromotionWorkflowMutation> mutation) {
            if (fail) {
                throw new RepositoryException(StorageFailureCode.STORAGE_UNAVAILABLE, "simulated");
            }
            mutation.nextState().updatedAccount().ifPresent(accounts::update);
            commitCount++;
            lastAudit = mutation.auditEvent();
        }
    }
}

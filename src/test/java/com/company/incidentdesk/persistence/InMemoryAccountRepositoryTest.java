package com.company.incidentdesk.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.persistence.memory.InMemoryAccountRepository;

/** Contract tests for the in-memory account repository. */
class InMemoryAccountRepositoryTest {
    private static final AccountId FIRST_ID = accountId("c2486a9d-48cb-4485-8762-eccfd46ae724");
    private static final AccountId SECOND_ID = accountId("fb2ae05d-b964-4550-8499-13d26a26ff6b");

    @Test
    void createAndLookupPreserveCaseSensitiveLoginNames() {
        InMemoryAccountRepository repository = new InMemoryAccountRepository();
        Account upperCaseAccount = account(FIRST_ID, "Alice", AccountStatus.ENABLED);
        Account lowerCaseAccount = account(SECOND_ID, "alice", AccountStatus.ENABLED);

        repository.create(upperCaseAccount);
        repository.create(lowerCaseAccount);

        assertEquals(upperCaseAccount, repository.findById(FIRST_ID).orElseThrow());
        assertEquals(upperCaseAccount, repository.findByLoginName("Alice").orElseThrow());
        assertEquals(lowerCaseAccount, repository.findByLoginName("alice").orElseThrow());
        assertTrue(repository.findByLoginName("ALICE").isEmpty());
        assertEquals(List.of(upperCaseAccount, lowerCaseAccount), repository.findAll());
        assertThrows(UnsupportedOperationException.class, () -> repository.findAll().clear());
    }

    @Test
    void updateRequiresExistingIdentifierAndMaintainsLoginIndex() {
        InMemoryAccountRepository repository = new InMemoryAccountRepository();
        Account original = account(FIRST_ID, "Alice", AccountStatus.ENABLED);
        Account updated = account(FIRST_ID, "AliceUpdated", AccountStatus.DISABLED);
        repository.create(original);

        repository.update(updated);

        assertEquals(updated, repository.findById(FIRST_ID).orElseThrow());
        assertTrue(repository.findByLoginName("Alice").isEmpty());
        assertEquals(updated, repository.findByLoginName("AliceUpdated").orElseThrow());
        RepositoryException failure = assertThrows(
                RepositoryException.class,
                () -> repository.update(account(SECOND_ID, "missing", AccountStatus.ENABLED)));
        assertEquals(StorageFailureCode.NOT_FOUND, failure.code());
    }

    @Test
    void createRejectsDuplicateIdentifierOrExactLoginName() {
        InMemoryAccountRepository repository = new InMemoryAccountRepository();
        repository.create(account(FIRST_ID, "Alice", AccountStatus.ENABLED));

        RepositoryException duplicateId = assertThrows(
                RepositoryException.class,
                () -> repository.create(account(FIRST_ID, "Different", AccountStatus.ENABLED)));
        RepositoryException duplicateLogin = assertThrows(
                RepositoryException.class,
                () -> repository.create(account(SECOND_ID, "Alice", AccountStatus.ENABLED)));

        assertEquals(StorageFailureCode.ALREADY_EXISTS, duplicateId.code());
        assertEquals(StorageFailureCode.ALREADY_EXISTS, duplicateLogin.code());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void failedRenameDoesNotChangeExistingIndexes() {
        InMemoryAccountRepository repository = new InMemoryAccountRepository();
        Account first = account(FIRST_ID, "Alice", AccountStatus.ENABLED);
        Account second = account(SECOND_ID, "Bob", AccountStatus.ENABLED);
        repository.create(first);
        repository.create(second);

        RepositoryException failure = assertThrows(
                RepositoryException.class,
                () -> repository.update(account(FIRST_ID, "Bob", AccountStatus.DISABLED)));

        assertEquals(StorageFailureCode.ALREADY_EXISTS, failure.code());
        assertEquals(first, repository.findByLoginName("Alice").orElseThrow());
        assertEquals(second, repository.findByLoginName("Bob").orElseThrow());
    }

    private static Account account(AccountId id, String loginName, AccountStatus status) {
        return new Account(id, loginName, Role.REPORTER, status, ResponderAccess.NONE);
    }

    private static AccountId accountId(String value) {
        return new AccountId(UUID.fromString(value));
    }
}

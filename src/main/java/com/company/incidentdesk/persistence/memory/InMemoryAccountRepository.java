package com.company.incidentdesk.persistence.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.persistence.AccountRepository;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** In-memory account repository with exact, case-sensitive login-name lookup. */
public final class InMemoryAccountRepository implements AccountRepository {
    private final Map<AccountId, Account> accountsById = new LinkedHashMap<>();
    private final Map<String, AccountId> accountIdsByLoginName = new LinkedHashMap<>();

    @Override
    public synchronized void create(Account account) {
        Account requiredAccount = Objects.requireNonNull(account, "account");
        if (accountsById.containsKey(requiredAccount.id())
                || accountIdsByLoginName.containsKey(requiredAccount.loginName())) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "account already exists");
        }
        accountsById.put(requiredAccount.id(), requiredAccount);
        accountIdsByLoginName.put(requiredAccount.loginName(), requiredAccount.id());
    }

    @Override
    public synchronized void update(Account account) {
        Account requiredAccount = Objects.requireNonNull(account, "account");
        Account existing = accountsById.get(requiredAccount.id());
        if (existing == null) {
            throw new RepositoryException(StorageFailureCode.NOT_FOUND, "account does not exist");
        }
        AccountId loginOwner = accountIdsByLoginName.get(requiredAccount.loginName());
        if (loginOwner != null && !loginOwner.equals(requiredAccount.id())) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "login name already exists");
        }
        accountIdsByLoginName.remove(existing.loginName());
        accountsById.put(requiredAccount.id(), requiredAccount);
        accountIdsByLoginName.put(requiredAccount.loginName(), requiredAccount.id());
    }

    @Override
    public synchronized Optional<Account> findById(AccountId accountId) {
        return Optional.ofNullable(accountsById.get(Objects.requireNonNull(accountId, "accountId")));
    }

    @Override
    public synchronized Optional<Account> findByLoginName(String loginName) {
        AccountId accountId = accountIdsByLoginName.get(Objects.requireNonNull(loginName, "loginName"));
        return Optional.ofNullable(accountId).flatMap(this::findById);
    }

    @Override
    public synchronized List<Account> findAll() {
        return List.copyOf(accountsById.values());
    }
}

package com.company.incidentdesk.persistence;

import java.util.List;
import java.util.Optional;

import com.company.incidentdesk.application.account.AccountLookup;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;

/** Storage-independent account repository. */
public interface AccountRepository extends AccountLookup {
    void create(Account account);

    void update(Account account);

    @Override
    Optional<Account> findById(AccountId accountId);

    @Override
    Optional<Account> findByLoginName(String loginName);

    List<Account> findAll();
}

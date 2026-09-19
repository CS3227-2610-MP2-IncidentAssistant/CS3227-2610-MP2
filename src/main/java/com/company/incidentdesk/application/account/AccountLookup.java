package com.company.incidentdesk.application.account;

import java.util.Optional;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;

/** Storage-independent account lookup operations used by application services. */
public interface AccountLookup {
    /**
     * Finds an account by its stable identifier.
     *
     * @param accountId account identifier
     * @return matching account, or empty when unavailable
     */
    Optional<Account> findById(AccountId accountId);

    /**
     * Finds an account by an exact, case-sensitive login name.
     *
     * @param loginName exact login name
     * @return matching account, or empty when unavailable
     */
    Optional<Account> findByLoginName(String loginName);
}

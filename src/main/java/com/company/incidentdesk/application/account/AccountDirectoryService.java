package com.company.incidentdesk.application.account;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.persistence.AccountRepository;
import com.company.incidentdesk.persistence.RepositoryException;

/** Supplies administrator-authorized account directory snapshots. */
public final class AccountDirectoryService {
    private final AccountAuthorizationPolicy authorization;
    private final AccountRepository accounts;

    public AccountDirectoryService(AccountAuthorizationPolicy authorization, AccountRepository accounts) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    public ApplicationResult<List<Account>> listAccounts() {
        if (!authorization.authorizeAccountDirectory().isAllowed()) {
            return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
        }
        try {
            List<Account> result = accounts.findAll().stream()
                    .filter(account -> !account.isDeleted())
                    .sorted(Comparator.comparing(Account::loginName)).toList();
            if (!authorization.authorizeAccountDirectory().isAllowed()) {
                return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
            }
            return ApplicationResult.success(result);
        } catch (RepositoryException exception) {
            return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.PERSISTENCE_FAILURE));
        }
    }
}

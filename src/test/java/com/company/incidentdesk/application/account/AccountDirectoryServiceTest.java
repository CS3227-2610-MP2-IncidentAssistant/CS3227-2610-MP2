package com.company.incidentdesk.application.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.persistence.memory.InMemoryAccountRepository;

class AccountDirectoryServiceTest {
    @Test
    void administratorCanListAccountsInLoginNameOrder() {
        Account administrator = account("admin", Role.ADMINISTRATOR);
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        accounts.create(account("zeta", Role.REPORTER));
        accounts.create(account("alpha", Role.REPORTER));
        MutableSessions sessions = new MutableSessions(administrator);

        var result = new AccountDirectoryService(
                new AccountAuthorizationPolicy(sessions), accounts).listAccounts();

        assertEquals(List.of("alpha", "zeta"), result.value().orElseThrow().stream()
                .map(Account::loginName).toList());
    }

    @Test
    void reporterAndMissingSessionCannotListAccounts() {
        MutableSessions sessions = new MutableSessions(account("reporter", Role.REPORTER));
        AccountDirectoryService service = new AccountDirectoryService(
                new AccountAuthorizationPolicy(sessions), new InMemoryAccountRepository());
        assertFalse(service.listAccounts().isSuccess());
        sessions.account = null;
        assertFalse(service.listAccounts().isSuccess());
    }

    private static Account account(String loginName, Role role) {
        return new Account(new AccountId(UUID.randomUUID()), loginName, role,
                AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    private static final class MutableSessions implements SessionProvider {
        private Account account;

        private MutableSessions(Account account) {
            this.account = account;
        }

        @Override public Optional<AuthenticatedSession> currentSession() {
            return Optional.ofNullable(account)
                    .map(value -> new AuthenticatedSession(value.id(), Instant.EPOCH));
        }

        @Override public Optional<Account> currentAccount() {
            return Optional.ofNullable(account);
        }
    }
}

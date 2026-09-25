package com.company.incidentdesk.application.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActor;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.persistence.memory.InMemoryAccountRepository;
import com.company.incidentdesk.persistence.memory.InMemoryAuditRepository;

class AuditLogServiceTest {
    private static final AccountId ADMIN_ID = id("10000000-0000-0000-0000-000000000001");
    private static final AccountId REPORTER_ID = id("10000000-0000-0000-0000-000000000002");

    @Test
    void administratorReceivesNewestFirstPrivacySafeEntries() {
        Account admin = account(ADMIN_ID, "admin", Role.ADMINISTRATOR);
        Account reporter = account(REPORTER_ID, "private-reporter", Role.REPORTER);
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        accounts.create(admin);
        accounts.create(reporter);
        InMemoryAuditRepository audits = new InMemoryAuditRepository();
        audits.append(event("20000000-0000-0000-0000-000000000001", Instant.parse("2026-01-01T00:00:00Z"),
                AuditActorVisibility.STANDARD));
        audits.append(event("20000000-0000-0000-0000-000000000002", Instant.parse("2026-01-02T00:00:00Z"),
                AuditActorVisibility.ANONYMOUS_REPORTER));

        var result = service(admin, accounts, audits).listAuditLog();

        assertTrue(result.isSuccess());
        List<AuditLogEntry> entries = result.value().orElseThrow();
        assertEquals(List.of(Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")),
                entries.stream().map(AuditLogEntry::occurredAt).toList());
        assertEquals(AuditActorLabelResolver.ANONYMOUS_REPORTER_LABEL, entries.get(0).actorLabel());
        assertEquals("private-reporter", entries.get(1).actorLabel());
        assertEquals("incident INC-123 created.", entries.get(0).eventDescription());
    }

    @Test
    void nonAdministratorCannotReadAuditLog() {
        Account reporter = account(REPORTER_ID, "reporter", Role.REPORTER);
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        accounts.create(reporter);

        var result = service(reporter, accounts, new InMemoryAuditRepository()).listAuditLog();

        assertTrue(!result.isSuccess());
        assertTrue(result.value().isEmpty());
    }

    @Test
    void accountEventDescriptionIncludesTargetUsername() {
        Account admin = account(ADMIN_ID, "admin", Role.ADMINISTRATOR);
        Account reporter = account(REPORTER_ID, "alex", Role.REPORTER);
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        accounts.create(admin);
        accounts.create(reporter);
        InMemoryAuditRepository audits = new InMemoryAuditRepository();
        audits.append(new AuditEvent(
                new AuditEventId(UUID.fromString("20000000-0000-0000-0000-000000000003")),
                Instant.parse("2026-01-03T00:00:00Z"),
                new AuditActor(REPORTER_ID, Role.REPORTER, AuditActorVisibility.STANDARD),
                AuditAction.ACCOUNT_REGISTERED,
                new AuditTarget(AuditTargetType.ACCOUNT, REPORTER_ID.value().toString()),
                AuditOutcome.SUCCESS,
                List.of(),
                Optional.empty()));

        AuditLogEntry entry = service(admin, accounts, audits).listAuditLog().value().orElseThrow().getFirst();

        assertEquals("account " + REPORTER_ID.value() + " with username alex registered.",
                entry.eventDescription());
    }

    private static AuditLogService service(
            Account current, InMemoryAccountRepository accounts, InMemoryAuditRepository audits) {
        SessionProvider session = new FixedSession(current);
        return new AuditLogService(
                new AccountAuthorizationPolicy(session), audits, new AuditActorLabelResolver(accounts), accounts);
    }

    private static AuditEvent event(String id, Instant time, AuditActorVisibility visibility) {
        return new AuditEvent(
                new AuditEventId(UUID.fromString(id)),
                time,
                new AuditActor(REPORTER_ID, Role.REPORTER, visibility),
                AuditAction.INCIDENT_CREATED,
                new AuditTarget(AuditTargetType.INCIDENT, "INC-123"),
                AuditOutcome.SUCCESS,
                List.of(),
                Optional.empty());
    }

    private static Account account(AccountId id, String loginName, Role role) {
        return new Account(id, loginName, role, AccountStatus.ENABLED, ResponderAccess.to(Set.of()));
    }

    private static AccountId id(String value) {
        return new AccountId(UUID.fromString(value));
    }

    private record FixedSession(Account account) implements SessionProvider {
        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return Optional.of(new AuthenticatedSession(account.id(), Instant.EPOCH));
        }

        @Override
        public Optional<Account> currentAccount() {
            return Optional.of(account);
        }
    }
}

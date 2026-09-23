package com.company.incidentdesk.application.slo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditChangeField;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.domain.slo.SloTargetVersionId;
import com.company.incidentdesk.persistence.memory.InMemorySloConfigurationStore;

class SloConfigurationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-19T08:00:00Z");
    private static final AccountId ADMIN_ID = accountId(1);
    private static final AccountId REPORTER_ID = accountId(2);

    @Test
    void deniesConfigureForANonAdministrator() {
        FakeSessionProvider session = new FakeSessionProvider();
        session.signIn(reporter());
        SloConfigurationService service = service(session, idSequence());

        var result = service.configure(IncidentCategory.IT, Duration.ofMinutes(30), Duration.ofHours(4), 0.1);

        assertTrue(result.error().orElseThrow().code() == ApplicationErrorCode.RESOURCE_UNAVAILABLE);
    }

    @Test
    void deniesConfigureWhenSignedOut() {
        FakeSessionProvider session = new FakeSessionProvider();
        SloConfigurationService service = service(session, idSequence());

        var result = service.configure(IncidentCategory.IT, Duration.ofMinutes(30), Duration.ofHours(4), 0.1);

        assertTrue(result.error().orElseThrow().code() == ApplicationErrorCode.RESOURCE_UNAVAILABLE);
    }

    @Test
    void rejectsNegativeDurationsAndOutOfRangeReopenRate() {
        FakeSessionProvider session = new FakeSessionProvider();
        session.signIn(administrator());
        SloConfigurationService service = service(session, idSequence());

        var result = service.configure(IncidentCategory.IT, Duration.ofSeconds(-1), Duration.ofHours(4), 1.5);

        assertEquals(ApplicationErrorCode.VALIDATION, result.error().orElseThrow().code());
        assertEquals(2, result.error().orElseThrow().validation().errors().size());
    }

    @Test
    void firstConfigurationForACategoryRecordsAnAddedAuditChange() {
        FakeSessionProvider session = new FakeSessionProvider();
        session.signIn(administrator());
        InMemorySloConfigurationStore store = new InMemorySloConfigurationStore();
        SloConfigurationService service = service(session, store, idSequence());

        SloTargetVersion version = service.configure(
                IncidentCategory.IT, Duration.ofMinutes(30), Duration.ofHours(4), 0.1)
                .value().orElseThrow();

        assertEquals(IncidentCategory.IT, version.category());
        assertEquals(ADMIN_ID, version.changedBy());
        assertEquals(NOW, version.effectiveFrom());
        AuditEvent auditEvent = store.auditEvents().get(0);
        assertEquals(AuditAction.SLO_CONFIGURATION_CHANGED, auditEvent.action());
        assertEquals(1, auditEvent.changes().size());
        assertEquals(AuditChangeField.SLO_VERSION, auditEvent.changes().get(0).field());
        assertTrue(auditEvent.changes().get(0).beforeValue().isEmpty());
    }

    @Test
    void aSecondConfigurationRecordsAChangedAuditEntryAndPreservesHistory() {
        FakeSessionProvider session = new FakeSessionProvider();
        session.signIn(administrator());
        InMemorySloConfigurationStore store = new InMemorySloConfigurationStore();
        SloConfigurationService service = service(session, store, idSequence());

        SloTargetVersion first = service.configure(
                IncidentCategory.IT, Duration.ofMinutes(30), Duration.ofHours(4), 0.1).value().orElseThrow();
        SloTargetVersion second = service.configure(
                IncidentCategory.IT, Duration.ofMinutes(15), Duration.ofHours(2), 0.2).value().orElseThrow();

        AuditEvent secondAudit = store.auditEvents().get(1);
        assertEquals(first.id().value().toString(), secondAudit.changes().get(0).beforeValue().orElseThrow());
        assertEquals(second.id().value().toString(), secondAudit.changes().get(0).afterValue().orElseThrow());

        List<SloTargetVersion> history = service.history(IncidentCategory.IT).value().orElseThrow();
        assertEquals(List.of(first, second), history);

        List<SloTargetVersion> current = service.currentTargets().value().orElseThrow();
        assertEquals(List.of(second), current);
    }

    @Test
    void duplicateAuditIdentifierSurfacesAsPersistenceFailure() {
        FakeSessionProvider session = new FakeSessionProvider();
        session.signIn(administrator());
        InMemorySloConfigurationStore store = new InMemorySloConfigurationStore();
        AuditEventId fixedAuditId = new AuditEventId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
        AuditEventFactory auditEventFactory = new AuditEventFactory(
                Clock.fixed(NOW, ZoneOffset.UTC), () -> fixedAuditId);
        SloConfigurationService service = new SloConfigurationService(
                session, new AccountAuthorizationPolicy(session), store, auditEventFactory,
                Clock.fixed(NOW, ZoneOffset.UTC), idSequence());

        service.configure(IncidentCategory.IT, Duration.ofMinutes(30), Duration.ofHours(4), 0.1);
        var second = service.configure(IncidentCategory.FACILITIES, Duration.ofMinutes(30), Duration.ofHours(4), 0.1);

        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, second.error().orElseThrow().code());
    }

    private static SloConfigurationService service(
            FakeSessionProvider session, java.util.function.Supplier<SloTargetVersionId> idGenerator) {
        return service(session, new InMemorySloConfigurationStore(), idGenerator);
    }

    private static SloConfigurationService service(
            FakeSessionProvider session,
            InMemorySloConfigurationStore store,
            java.util.function.Supplier<SloTargetVersionId> idGenerator) {
        AuditEventFactory auditEventFactory = new AuditEventFactory(
                Clock.fixed(NOW, ZoneOffset.UTC), auditIdSequence());
        return new SloConfigurationService(
                session, new AccountAuthorizationPolicy(session), store, auditEventFactory,
                Clock.fixed(NOW, ZoneOffset.UTC), idGenerator);
    }

    private static java.util.function.Supplier<SloTargetVersionId> idSequence() {
        AtomicInteger counter = new AtomicInteger(1);
        return () -> new SloTargetVersionId(new UUID(0, counter.getAndIncrement()));
    }

    private static java.util.function.Supplier<AuditEventId> auditIdSequence() {
        AtomicInteger counter = new AtomicInteger(1);
        return () -> new AuditEventId(new UUID(1, counter.getAndIncrement()));
    }

    private static Account administrator() {
        return new Account(ADMIN_ID, "admin", Role.ADMINISTRATOR, AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    private static Account reporter() {
        return new Account(REPORTER_ID, "reporter", Role.REPORTER, AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    private static AccountId accountId(long value) {
        return new AccountId(new UUID(0, value));
    }

    /** Minimal mutable session double for authorization-dependent service tests. */
    private static final class FakeSessionProvider implements SessionProvider {
        private Optional<Account> account = Optional.empty();

        void signIn(Account currentAccount) {
            account = Optional.of(currentAccount);
        }

        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return account.map(current -> new AuthenticatedSession(current.id(), NOW));
        }

        @Override
        public Optional<Account> currentAccount() {
            return account.filter(Account::isEnabled);
        }
    }
}

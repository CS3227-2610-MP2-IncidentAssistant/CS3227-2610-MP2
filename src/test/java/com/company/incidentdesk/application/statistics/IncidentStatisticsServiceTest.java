package com.company.incidentdesk.application.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.authorization.StatisticsAuthorizationPolicy;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.persistence.memory.InMemoryAccountRepository;
import com.company.incidentdesk.persistence.memory.InMemoryIncidentRepository;

/** Verifies authorization, scoping, and anonymity safety of shared statistics queries. */
class IncidentStatisticsServiceTest {
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final AccountId REPORTER_ID = accountId(1);
    private static final AccountId RESPONDER_ID = accountId(3);
    private static final AccountId OTHER_RESPONDER_ID = accountId(4);
    private static final AccountId ADMIN_ID = accountId(5);

    private InMemoryIncidentRepository incidents;
    private InMemoryAccountRepository accounts;
    private FakeSessionProvider sessionProvider;
    private IncidentStatisticsService service;

    @BeforeEach
    void setUp() {
        incidents = new InMemoryIncidentRepository();
        accounts = new InMemoryAccountRepository();
        sessionProvider = new FakeSessionProvider();
        service = new IncidentStatisticsService(
                incidents, accounts, new StatisticsAuthorizationPolicy(sessionProvider));
        accounts.create(account(RESPONDER_ID, Role.RESPONDER, "responder-one"));
        accounts.create(account(OTHER_RESPONDER_ID, Role.RESPONDER, "responder-two"));
        accounts.create(account(ADMIN_ID, Role.ADMINISTRATOR, "admin"));
    }

    @Test
    void responderCanViewOwnPerformanceButNotAnotherResponders() {
        sessionProvider.signIn(account(RESPONDER_ID, Role.RESPONDER, "responder-one"));

        ApplicationResult<StatisticsResult> own = service.responderPerformance(
                RESPONDER_ID, StatisticsQuery.defaults());
        ApplicationResult<StatisticsResult> other = service.responderPerformance(
                OTHER_RESPONDER_ID, StatisticsQuery.defaults());

        assertTrue(own.isSuccess());
        assertFalse(other.isSuccess());
    }

    @Test
    void reporterCannotViewAnyStatistics() {
        sessionProvider.signIn(account(REPORTER_ID, Role.REPORTER, "reporter-one"));

        assertFalse(service.responderPerformance(RESPONDER_ID, StatisticsQuery.defaults()).isSuccess());
        assertFalse(service.companyStatistics(StatisticsQuery.defaults(), true).isSuccess());
    }

    @Test
    void onlyAdministratorsCanViewCompanyStatistics() {
        sessionProvider.signIn(account(RESPONDER_ID, Role.RESPONDER, "responder-one"));
        assertFalse(service.companyStatistics(StatisticsQuery.defaults(), true).isSuccess());

        sessionProvider.signIn(account(ADMIN_ID, Role.ADMINISTRATOR, "admin"));
        assertTrue(service.companyStatistics(StatisticsQuery.defaults(), true).isSuccess());
    }

    @Test
    void companyStatisticsAttributesResolutionsAndOmitsTimeToClaimFromResponderBreakdown() {
        seedResolvedIncident(1, RESPONDER_ID, false);
        sessionProvider.signIn(account(ADMIN_ID, Role.ADMINISTRATOR, "admin"));

        ApplicationResult<StatisticsResult> result = service.companyStatistics(StatisticsQuery.defaults(), true);

        assertTrue(result.isSuccess());
        StatisticsResult value = result.value().orElseThrow();
        assertTrue(value.companySummary().isPresent());
        assertEquals(1, value.companySummary().orElseThrow().aggregate().resolvedIncidentCount());
        assertEquals(1, value.responders().size());
        assertEquals(RESPONDER_ID, value.responders().get(0).responderId());
        assertEquals("responder-one", value.responders().get(0).displayName());
        assertEquals(3, value.series().size());
    }

    @Test
    void responderPerformanceNeverIncludesACompanyWideSummary() {
        seedResolvedIncident(2, RESPONDER_ID, false);
        sessionProvider.signIn(account(RESPONDER_ID, Role.RESPONDER, "responder-one"));

        StatisticsResult result = service.responderPerformance(RESPONDER_ID, StatisticsQuery.defaults())
                .value().orElseThrow();

        assertTrue(result.companySummary().isEmpty());
        assertEquals(1, result.responders().size());
        assertEquals(1, result.responders().get(0).statistics().resolvedCycleCount());
    }

    @Test
    void reporterFilterExcludesAnonymousIncidentsEvenWhenTheirInternalReporterIdMatches() {
        seedResolvedIncident(3, RESPONDER_ID, true);
        sessionProvider.signIn(account(ADMIN_ID, Role.ADMINISTRATOR, "admin"));

        StatisticsQuery query = new StatisticsQuery(
                Set.of(), Optional.of(REPORTER_ID), Optional.empty(), Optional.empty());
        StatisticsResult result = service.companyStatistics(query, true).value().orElseThrow();

        assertEquals(0, result.companySummary().orElseThrow().aggregate().resolvedIncidentCount());
        assertTrue(result.responders().isEmpty());
    }

    @Test
    void unknownResponderPerformanceIsDeterministicallyEmptyRatherThanAnError() {
        sessionProvider.signIn(account(ADMIN_ID, Role.ADMINISTRATOR, "admin"));

        StatisticsResult result = service.responderPerformance(OTHER_RESPONDER_ID, StatisticsQuery.defaults())
                .value().orElseThrow();

        assertEquals(1, result.responders().size());
        assertEquals(0, result.responders().get(0).statistics().resolvedCycleCount());
    }

    private void seedResolvedIncident(long id, AccountId responderId, boolean anonymous) {
        Instant submittedAt = BASE;
        Instant claimedAt = submittedAt.plusSeconds(5);
        Instant resolvedAt = claimedAt.plusSeconds(30);
        Incident incident = lifecycleAt(submittedAt).submit(
                new IncidentId(uuid(id)), REPORTER_ID, "Title", "Description", IncidentCategory.IT, anonymous);
        incident = lifecycleAt(claimedAt).claim(incident, responderId);
        incident = lifecycleAt(resolvedAt).resolve(incident, responderId, "Fixed");
        incidents.create(incident);
    }

    private static Account account(AccountId id, Role role, String loginName) {
        ResponderAccess access = role == Role.RESPONDER
                ? ResponderAccess.to(Set.of(IncidentCategory.IT))
                : ResponderAccess.NONE;
        return new Account(id, loginName, role, AccountStatus.ENABLED, access);
    }

    private static IncidentLifecycle lifecycleAt(Instant instant) {
        return new IncidentLifecycle(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static AccountId accountId(long value) {
        return new AccountId(uuid(value));
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }

    private static final class FakeSessionProvider implements SessionProvider {
        private Optional<Account> account = Optional.empty();

        void signIn(Account currentAccount) {
            account = Optional.of(currentAccount);
        }

        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return account.map(current -> new AuthenticatedSession(current.id(), BASE));
        }

        @Override
        public Optional<Account> currentAccount() {
            return account.filter(Account::isEnabled);
        }
    }
}

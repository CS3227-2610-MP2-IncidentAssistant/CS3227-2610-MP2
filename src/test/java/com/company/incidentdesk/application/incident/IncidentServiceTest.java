package com.company.incidentdesk.application.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.comment.CommentType;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentAction;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.persistence.IncidentSort;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;
import com.company.incidentdesk.persistence.memory.InMemoryAccountRepository;
import com.company.incidentdesk.persistence.memory.InMemoryIncidentRepository;

class IncidentServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-23T08:00:00Z");
    private static final AccountId REPORTER_ID = accountId("00000000-0000-0000-0000-000000000001");
    private static final AccountId OTHER_REPORTER_ID = accountId("00000000-0000-0000-0000-000000000002");
    private static final AccountId RESPONDER_ID = accountId("00000000-0000-0000-0000-000000000003");
    private static final AccountId ADMIN_ID = accountId("00000000-0000-0000-0000-000000000004");
    private static final AccountId SECOND_RESPONDER_ID = accountId("00000000-0000-0000-0000-000000000005");
    private static final IncidentId INCIDENT_ID = incidentId("10000000-0000-0000-0000-000000000001");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MutableSessionProvider sessions = new MutableSessionProvider();
    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final List<IncidentChangedEvent> events = new ArrayList<>();
    private final AtomicInteger auditSequence = new AtomicInteger();
    private InMemoryIncidentRepository incidents;
    private IncidentService service;

    @BeforeEach
    void setUp() {
        accounts.create(reporter(REPORTER_ID));
        accounts.create(reporter(OTHER_REPORTER_ID));
        accounts.create(responder(RESPONDER_ID, IncidentCategory.IT));
        accounts.create(responder(SECOND_RESPONDER_ID, IncidentCategory.IT));
        accounts.create(administrator());
        incidents = new InMemoryIncidentRepository();
        service = serviceUsing(incidents);
    }

    @Test
    void reporterCanCreateAndReadOwnSubmittedIncident() {
        sessions.signIn(accounts.findById(REPORTER_ID).orElseThrow());

        ApplicationResult<IncidentView> submitted = service.submit(
                "  Printer unavailable  ", "Paper remains jammed", IncidentCategory.IT, true);

        assertTrue(submitted.isSuccess());
        assertEquals("Printer unavailable", submitted.value().orElseThrow().title());
        assertEquals(IncidentStatus.SUBMITTED, submitted.value().orElseThrow().status());
        assertEquals(1, incidents.auditEvents().size());
        assertEquals(AuditActorVisibility.ANONYMOUS_REPORTER,
                incidents.auditEvents().getFirst().actor().visibility());
        assertEquals(List.of(new IncidentChangedEvent(INCIDENT_ID, IncidentAction.SUBMIT)), events);
        assertTrue(service.detail(INCIDENT_ID).isSuccess());
    }

    @Test
    void invalidSubmissionDoesNotPersistOrPublish() {
        sessions.signIn(accounts.findById(REPORTER_ID).orElseThrow());

        ApplicationResult<IncidentView> result = service.submit(" ", "", IncidentCategory.IT, false);

        assertEquals(ApplicationErrorCode.VALIDATION, result.error().orElseThrow().code());
        assertTrue(incidents.findById(INCIDENT_ID).isEmpty());
        assertTrue(incidents.auditEvents().isEmpty());
        assertTrue(events.isEmpty());
    }

    @Test
    void reporterCanSaveEditAndSubmitDraft() {
        sessions.signIn(accounts.findById(REPORTER_ID).orElseThrow());
        assertTrue(service.saveDraft("", "", IncidentCategory.IT, false).isSuccess());

        assertTrue(service.edit(INCIDENT_ID, "  Updated title  ", "Updated description",
                IncidentCategory.FACILITIES, true).isSuccess());
        ApplicationResult<IncidentView> submitted = service.submitDraft(INCIDENT_ID);

        assertTrue(submitted.isSuccess());
        assertEquals("Updated title", submitted.value().orElseThrow().title());
        assertEquals(IncidentCategory.FACILITIES, submitted.value().orElseThrow().category());
        assertEquals(IncidentStatus.SUBMITTED, submitted.value().orElseThrow().status());
        assertEquals(3, incidents.auditEvents().size());
    }

    @Test
    void reporterCanWithdrawUnassignedSubmission() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(REPORTER_ID).orElseThrow());

        assertTrue(service.withdraw(INCIDENT_ID).isSuccess());

        assertEquals(IncidentStatus.WITHDRAWN, incidents.findById(INCIDENT_ID).orElseThrow().status());
    }

    @Test
    void anotherReporterCannotDiscoverOrWithdrawIncident() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(OTHER_REPORTER_ID).orElseThrow());

        ApplicationResult<IncidentView> detail = service.detail(INCIDENT_ID);
        ApplicationResult<IncidentView> withdrawal = service.withdraw(INCIDENT_ID);

        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, detail.error().orElseThrow().code());
        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, withdrawal.error().orElseThrow().code());
        assertEquals(IncidentStatus.SUBMITTED, incidents.findById(INCIDENT_ID).orElseThrow().status());
    }

    @Test
    void responderCanClaimAndResolveEligibleIncident() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());

        assertTrue(service.claim(INCIDENT_ID).isSuccess());
        assertTrue(service.resolve(INCIDENT_ID, " Replaced pickup roller ").isSuccess());

        Incident resolved = incidents.findById(INCIDENT_ID).orElseThrow();
        assertEquals(IncidentStatus.RESOLVED, resolved.status());
        assertEquals(" Replaced pickup roller ",
                resolved.currentCycle().orElseThrow().resolution().orElseThrow().remarks());
        assertEquals(3, incidents.auditEvents().size());
        assertEquals(3, events.size());
    }

    @Test
    void responderLosingCategoryAccessCannotResolveExistingAssignment() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());
        assertTrue(service.claim(INCIDENT_ID).isSuccess());
        Account noAccess = responder(RESPONDER_ID);
        accounts.update(noAccess);
        sessions.signIn(noAccess);

        ApplicationResult<IncidentView> result = service.resolve(INCIDENT_ID, "Done");

        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, result.error().orElseThrow().code());
        assertEquals(IncidentStatus.ASSIGNED, incidents.findById(INCIDENT_ID).orElseThrow().status());
    }

    @Test
    void responderCanHandoffAndAdministratorCanReassignAndResolve() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());
        assertTrue(service.claim(INCIDENT_ID).isSuccess());
        assertTrue(service.handoff(INCIDENT_ID).isSuccess());

        assertTrue(service.claim(INCIDENT_ID).isSuccess());
        sessions.signIn(accounts.findById(ADMIN_ID).orElseThrow());
        assertTrue(service.reassign(INCIDENT_ID, SECOND_RESPONDER_ID).isSuccess());
        assertTrue(service.resolve(INCIDENT_ID, "Administrator verified resolution").isSuccess());

        Incident resolved = incidents.findById(INCIDENT_ID).orElseThrow();
        assertEquals(IncidentStatus.RESOLVED, resolved.status());
        assertEquals(SECOND_RESPONDER_ID,
                resolved.currentCycle().orElseThrow().resolution().orElseThrow().responderAtResolution());
    }

    @Test
    void administratorCannotReassignToIneligibleResponder() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());
        assertTrue(service.claim(INCIDENT_ID).isSuccess());
        accounts.update(responder(SECOND_RESPONDER_ID, IncidentCategory.FACILITIES));
        sessions.signIn(accounts.findById(ADMIN_ID).orElseThrow());

        ApplicationResult<IncidentView> result = service.reassign(INCIDENT_ID, SECOND_RESPONDER_ID);

        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, result.error().orElseThrow().code());
        assertEquals(RESPONDER_ID, incidents.findById(INCIDENT_ID).orElseThrow().assigneeId().orElseThrow());
    }

    @Test
    void reopenCommitsIncidentExplanationAndAuditTogether() {
        createResolvedIncident();
        sessions.signIn(accounts.findById(REPORTER_ID).orElseThrow());

        ApplicationResult<IncidentView> result = service.reopen(INCIDENT_ID, "The fault returned");

        assertTrue(result.isSuccess());
        assertEquals(IncidentStatus.SUBMITTED, incidents.findById(INCIDENT_ID).orElseThrow().status());
        assertEquals("The fault returned", incidents.reopenExplanations().getFirst().text());
        assertEquals("The fault returned", incidents.findCommentsByIncidentId(INCIDENT_ID).getFirst().text());
        assertEquals(CommentType.REOPEN_EXPLANATION,
                incidents.findCommentsByIncidentId(INCIDENT_ID).getFirst().type());
        assertEquals(4, incidents.auditEvents().size());
        assertEquals(4, events.size());
    }

    @Test
    void failedReopenCommitChangesNothingAndPublishesNothing() {
        createResolvedIncident();
        InMemoryIncidentRepository failingStore = copyIntoFailingStore();
        service = serviceUsing(failingStore);
        sessions.signIn(accounts.findById(REPORTER_ID).orElseThrow());
        int eventsBefore = events.size();

        ApplicationResult<IncidentView> result = service.reopen(INCIDENT_ID, "The fault returned");

        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, result.error().orElseThrow().code());
        assertEquals(IncidentStatus.RESOLVED, failingStore.findById(INCIDENT_ID).orElseThrow().status());
        assertTrue(failingStore.reopenExplanations().isEmpty());
        assertTrue(failingStore.findCommentsByIncidentId(INCIDENT_ID).isEmpty());
        assertTrue(failingStore.auditEvents().isEmpty());
        assertEquals(eventsBefore, events.size());
    }

    @Test
    void listUsesCurrentRoleAndCategoryAccess() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());
        assertEquals(1, service.list(IncidentSort.queueOrder()).value().orElseThrow().size());

        Account noAccess = responder(RESPONDER_ID);
        accounts.update(noAccess);
        sessions.signIn(noAccess);

        assertTrue(service.list(IncidentSort.queueOrder()).value().orElseThrow().isEmpty());
    }

    @Test
    void signedOutOperationsReturnNeutralUnavailableError() {
        ApplicationResult<IncidentView> result = service.detail(INCIDENT_ID);

        assertFalse(result.isSuccess());
        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, result.error().orElseThrow().code());
    }

    private void createSubmittedIncident(boolean anonymous) {
        sessions.signIn(accounts.findById(REPORTER_ID).orElseThrow());
        assertTrue(service.submit("Printer", "Jammed", IncidentCategory.IT, anonymous).isSuccess());
    }

    private void createResolvedIncident() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());
        assertTrue(service.claim(INCIDENT_ID).isSuccess());
        assertTrue(service.resolve(INCIDENT_ID, "Cleared").isSuccess());
    }

    private InMemoryIncidentRepository copyIntoFailingStore() {
        Incident existing = incidents.findById(INCIDENT_ID).orElseThrow();
        InMemoryIncidentRepository failing = new InMemoryIncidentRepository(ignored -> {
            throw new RepositoryException(StorageFailureCode.STORAGE_UNAVAILABLE, "simulated failure");
        });
        failing.create(existing);
        return failing;
    }

    private IncidentService serviceUsing(InMemoryIncidentRepository incidentStore) {
        return new IncidentService(
                sessions,
                incidentStore,
                accounts,
                new IncidentAuthorizationPolicy(sessions),
                new IncidentLifecycle(clock),
                new AuditEventFactory(clock, this::nextAuditEventId),
                () -> INCIDENT_ID,
                events::add);
    }

    private AuditEventId nextAuditEventId() {
        return new AuditEventId(new UUID(0, auditSequence.incrementAndGet()));
    }

    private static Account reporter(AccountId id) {
        return new Account(id, "reporter-" + id.value(), Role.REPORTER,
                AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    private static Account responder(AccountId id, IncidentCategory... categories) {
        return new Account(id, "responder-" + id.value(), Role.RESPONDER,
                AccountStatus.ENABLED, ResponderAccess.to(Set.of(categories)));
    }

    private static Account administrator() {
        return new Account(ADMIN_ID, "administrator", Role.ADMINISTRATOR,
                AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    private static AccountId accountId(String value) {
        return new AccountId(UUID.fromString(value));
    }

    private static IncidentId incidentId(String value) {
        return new IncidentId(UUID.fromString(value));
    }

    private static final class MutableSessionProvider implements SessionProvider {
        private Optional<Account> current = Optional.empty();

        void signIn(Account account) {
            current = Optional.of(account);
        }

        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return current.map(account -> new AuthenticatedSession(account.id(), NOW));
        }

        @Override
        public Optional<Account> currentAccount() {
            return current;
        }
    }
}

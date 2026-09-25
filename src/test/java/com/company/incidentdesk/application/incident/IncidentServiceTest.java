package com.company.incidentdesk.application.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.presentation.ResponderDashboardModel;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
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
import com.company.incidentdesk.persistence.IncidentSearchCriteria;
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
        assertEquals(List.of(new IncidentChangedEvent(
                INCIDENT_ID, IncidentAction.SUBMIT, REPORTER_ID)), events);
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
    void claimRejectsSignedOutWrongRoleRevokedCategoryAndRepeatedAssignmentWithoutWrites() {
        createSubmittedIncident(true);
        Incident before = incidents.findById(INCIDENT_ID).orElseThrow();
        sessions.current = Optional.empty();
        assertFalse(service.claim(INCIDENT_ID).isSuccess());
        for (Account actor : List.of(reporter(REPORTER_ID), administrator(), responder(RESPONDER_ID),
                responder(RESPONDER_ID, IncidentCategory.FACILITIES))) {
            sessions.signIn(actor);
            assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE,
                    service.claim(INCIDENT_ID).error().orElseThrow().code());
        }
        assertEquals(before, incidents.findById(INCIDENT_ID).orElseThrow());
        assertEquals(1, incidents.auditEvents().size());
        assertEquals(1, events.size());

        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());
        assertTrue(service.claim(INCIDENT_ID).isSuccess());
        Incident claimed = incidents.findById(INCIDENT_ID).orElseThrow();
        assertFalse(service.claim(INCIDENT_ID).isSuccess());
        sessions.signIn(accounts.findById(SECOND_RESPONDER_ID).orElseThrow());
        assertFalse(service.claim(INCIDENT_ID).isSuccess());
        assertEquals(claimed, incidents.findById(INCIDENT_ID).orElseThrow());
        assertEquals(2, incidents.auditEvents().size());
        assertEquals(2, events.size());
    }

    @Test
    void withdrawnIncidentCannotBeClaimed() {
        createSubmittedIncident(false);
        assertTrue(service.withdraw(INCIDENT_ID).isSuccess());
        Incident before = incidents.findById(INCIDENT_ID).orElseThrow();
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());

        assertFalse(service.claim(INCIDENT_ID).isSuccess());

        assertEquals(before, incidents.findById(INCIDENT_ID).orElseThrow());
        assertEquals(2, incidents.auditEvents().size());
        assertEquals(2, events.size());
    }

    @Test
    void failedClaimCommitLeavesIncidentAuditAndNotificationsUnchanged() {
        createSubmittedIncident(false);
        InMemoryIncidentRepository failingStore = copyIntoFailingStore();
        Incident before = failingStore.findById(INCIDENT_ID).orElseThrow();
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());

        var result = serviceUsing(failingStore).claim(INCIDENT_ID);

        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, result.error().orElseThrow().code());
        assertEquals(before, failingStore.findById(INCIDENT_ID).orElseThrow());
        assertTrue(failingStore.auditEvents().isEmpty());
        assertEquals(1, events.size());
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
    void resolutionValidationAndFailedCommitPreserveAssignmentAndAudit() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());
        assertTrue(service.claim(INCIDENT_ID).isSuccess());
        Incident before = incidents.findById(INCIDENT_ID).orElseThrow();
        for (String remarks : List.of("", " \t\n ")) {
            var result = service.resolve(INCIDENT_ID, remarks);
            assertEquals(ApplicationErrorCode.VALIDATION, result.error().orElseThrow().code());
            assertEquals("incident.resolutionRemarks",
                    result.error().orElseThrow().validation().errors().getFirst().field().value());
        }
        assertEquals(before, incidents.findById(INCIDENT_ID).orElseThrow());
        assertEquals(2, incidents.auditEvents().size());
        InMemoryIncidentRepository failingStore = copyIntoFailingStore();

        var failed = serviceUsing(failingStore).resolve(INCIDENT_ID, "Repaired");

        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, failed.error().orElseThrow().code());
        assertEquals(before, failingStore.findById(INCIDENT_ID).orElseThrow());
        assertTrue(failingStore.auditEvents().isEmpty());
        assertEquals(2, events.size());
    }

    @Test
    void resolutionRechecksActorAssignmentAndStateWithoutDuplicateWrites() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());
        assertFalse(service.resolve(INCIDENT_ID, "Not assigned").isSuccess());
        assertTrue(service.claim(INCIDENT_ID).isSuccess());
        Incident before = incidents.findById(INCIDENT_ID).orElseThrow();
        sessions.current = Optional.empty();
        assertFalse(service.resolve(INCIDENT_ID, "Signed out").isSuccess());
        for (Account actor : List.of(reporter(REPORTER_ID), responder(SECOND_RESPONDER_ID, IncidentCategory.IT))) {
            sessions.signIn(actor);
            assertFalse(service.resolve(INCIDENT_ID, "Not authorized").isSuccess());
        }
        assertEquals(before, incidents.findById(INCIDENT_ID).orElseThrow());
        assertEquals(2, incidents.auditEvents().size());
        sessions.signIn(administrator());
        assertTrue(service.reassign(INCIDENT_ID, SECOND_RESPONDER_ID).isSuccess());
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());
        assertFalse(service.resolve(INCIDENT_ID, "Stale assignment").isSuccess());
        assertEquals(3, incidents.auditEvents().size());
        sessions.signIn(accounts.findById(SECOND_RESPONDER_ID).orElseThrow());
        assertTrue(service.resolve(INCIDENT_ID, "Done").isSuccess());
        assertFalse(service.resolve(INCIDENT_ID, "Repeat").isSuccess());
        assertEquals(4, incidents.auditEvents().size());
        assertEquals(4, events.size());
    }

    @Test
    void responderKeepsAbilityToResolveAfterLosingCategoryAccess() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());
        assertTrue(service.claim(INCIDENT_ID).isSuccess());
        Account noAccess = responder(RESPONDER_ID);
        accounts.update(noAccess);
        sessions.signIn(noAccess);

        ApplicationResult<IncidentView> result = service.resolve(INCIDENT_ID, "Done");

        assertTrue(result.isSuccess());
        assertEquals(IncidentStatus.RESOLVED, incidents.findById(INCIDENT_ID).orElseThrow().status());
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
    void administratorCanUnassignAnAssignedIncidentBackToTheQueue() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());
        assertTrue(service.claim(INCIDENT_ID).isSuccess());
        Incident claimed = incidents.findById(INCIDENT_ID).orElseThrow();
        sessions.signIn(accounts.findById(ADMIN_ID).orElseThrow());

        ApplicationResult<IncidentView> result = service.handoff(INCIDENT_ID);

        assertTrue(result.isSuccess());
        Incident unassigned = incidents.findById(INCIDENT_ID).orElseThrow();
        assertEquals(IncidentStatus.SUBMITTED, unassigned.status());
        assertTrue(unassigned.assigneeId().isEmpty());
        assertEquals(claimed.currentCycle().orElseThrow().queueEnteredAt(),
                unassigned.currentCycle().orElseThrow().queueEnteredAt());
        assertEquals(3, incidents.auditEvents().size());
        assertEquals(AuditAction.INCIDENT_HANDED_OFF, incidents.auditEvents().getLast().action());
    }

    @Test
    void administratorCannotUnassignAnUnassignedIncident() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(ADMIN_ID).orElseThrow());

        ApplicationResult<IncidentView> result = service.handoff(INCIDENT_ID);

        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, result.error().orElseThrow().code());
        assertEquals(1, incidents.auditEvents().size());
    }

    @Test
    void administratorCanAssignAnUnassignedSubmittedIncidentDirectly() {
        createSubmittedIncident(false);
        sessions.signIn(accounts.findById(ADMIN_ID).orElseThrow());

        ApplicationResult<IncidentView> result = service.reassign(INCIDENT_ID, RESPONDER_ID);

        assertTrue(result.isSuccess());
        Incident assigned = incidents.findById(INCIDENT_ID).orElseThrow();
        assertEquals(IncidentStatus.ASSIGNED, assigned.status());
        assertEquals(RESPONDER_ID, assigned.assigneeId().orElseThrow());
        assertEquals(2, incidents.auditEvents().size());
        assertEquals(AuditAction.INCIDENT_REASSIGNED, incidents.auditEvents().getLast().action());
    }

    @Test
    void administratorCannotAssignSubmittedIncidentToIneligibleResponder() {
        createSubmittedIncident(false);
        accounts.update(responder(SECOND_RESPONDER_ID, IncidentCategory.FACILITIES));
        sessions.signIn(accounts.findById(ADMIN_ID).orElseThrow());

        ApplicationResult<IncidentView> result = service.reassign(INCIDENT_ID, SECOND_RESPONDER_ID);

        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, result.error().orElseThrow().code());
        assertTrue(incidents.findById(INCIDENT_ID).orElseThrow().assigneeId().isEmpty());
        assertEquals(1, incidents.auditEvents().size());
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

    @Test
    void dashboardSeparatesAuthorizedQueuesAndRedactsAnonymousReporter() {
        IncidentLifecycle lifecycle = new IncidentLifecycle(clock);
        Incident eligible = dashboardIncident(1, IncidentCategory.IT, true);
        Incident assigned = lifecycle.claim(dashboardIncident(2, IncidentCategory.IT, false), RESPONDER_ID);
        Incident assignedOutsideCurrentAccess =
                lifecycle.claim(dashboardIncident(5, IncidentCategory.FACILITIES, false), RESPONDER_ID);
        incidents.create(eligible);
        incidents.create(assigned);
        incidents.create(lifecycle.claim(dashboardIncident(3, IncidentCategory.IT, false), SECOND_RESPONDER_ID));
        incidents.create(dashboardIncident(4, IncidentCategory.FACILITIES, false));
        incidents.create(assignedOutsideCurrentAccess);
        incidents.create(lifecycle.withdraw(dashboardIncident(6, IncidentCategory.IT, false)));
        incidents.create(lifecycle.saveDraft(new IncidentId(new UUID(1, 7)), REPORTER_ID,
                "Draft", "Private", IncidentCategory.IT, false));
        incidents.create(lifecycle.resolve(
                lifecycle.claim(dashboardIncident(8, IncidentCategory.IT, false), RESPONDER_ID),
                RESPONDER_ID, "Done"));
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());

        ResponderDashboardModel model = service.responderDashboard(dashboardMapper()).value().orElseThrow();

        assertEquals(List.of(eligible.id()), model.eligible().stream().map(IncidentRowModel::id).toList());
        assertEquals(List.of(assigned.id(), assignedOutsideCurrentAccess.id()),
                model.assigned().stream().map(IncidentRowModel::id).toList());
        assertEquals("Anonymous reporter", model.eligible().getFirst().reporterLabel());
        assertFalse(model.eligible().toString().contains(REPORTER_ID.value().toString()));
        assertTrue(incidents.auditEvents().isEmpty());
        assertTrue(events.isEmpty());
    }

    @Test
    void dashboardUsesQueueOrderWithStableTiesAndPreservesHandoffPosition() {
        IncidentLifecycle lifecycle = new IncidentLifecycle(clock);
        Incident first = dashboardIncident(1, IncidentCategory.IT, false);
        Incident second = dashboardIncident(2, IncidentCategory.IT, false);
        Incident handedOff = lifecycle.handoff(lifecycle.claim(first, RESPONDER_ID));
        incidents.create(second);
        incidents.create(handedOff);
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());

        ResponderDashboardModel model = service.responderDashboard(dashboardMapper()).value().orElseThrow();

        assertEquals(List.of(first.id(), second.id()), model.eligible().stream().map(IncidentRowModel::id).toList());
        assertEquals(first.currentCycle().orElseThrow().queueEnteredAt(),
                incidents.findById(first.id()).orElseThrow().currentCycle().orElseThrow().queueEnteredAt());
    }

    @Test
    void dashboardRechecksCategoryAccessForEligibleListButKeepsExistingAssignments() {
        incidents.create(dashboardIncident(1, IncidentCategory.IT, false));
        incidents.create(new IncidentLifecycle(clock).claim(dashboardIncident(2, IncidentCategory.IT, false), RESPONDER_ID));
        sessions.signIn(accounts.findById(RESPONDER_ID).orElseThrow());
        ResponderDashboardModel withAccess = service.responderDashboard(dashboardMapper()).value().orElseThrow();
        assertEquals(1, withAccess.eligible().size());
        assertEquals(1, withAccess.assigned().size());

        sessions.signIn(responder(RESPONDER_ID));

        ResponderDashboardModel withoutAccess = service.responderDashboard(dashboardMapper()).value().orElseThrow();
        assertTrue(withoutAccess.eligible().isEmpty());
        assertEquals(1, withoutAccess.assigned().size());
    }

    @Test
    void dashboardDeniesSignedOutReporterAdministratorAndDisabledResponder() {
        assertFalse(service.responderDashboard(dashboardMapper()).isSuccess());
        for (Account actor : List.of(reporter(REPORTER_ID), administrator(),
                new Account(RESPONDER_ID, "disabled", Role.RESPONDER, AccountStatus.DISABLED,
                        ResponderAccess.to(Set.of(IncidentCategory.IT))))) {
            sessions.signIn(actor);
            assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE,
                    service.responderDashboard(dashboardMapper()).error().orElseThrow().code());
        }
    }

    @Test
    void administratorIncidentViewReturnsAuthorizedPrivacySafeRows() {
        incidents.create(dashboardIncident(1, IncidentCategory.IT, false));
        incidents.create(dashboardIncident(2, IncidentCategory.FACILITIES, true));
        sessions.signIn(administrator());

        ApplicationResult<List<IncidentRowModel>> result = service.administratorIncidents(
                IncidentSearchCriteria.defaults(), dashboardMapper());

        assertTrue(result.isSuccess());
        assertEquals(2, result.value().orElseThrow().size());
        assertEquals("Anonymous reporter", result.value().orElseThrow().stream()
                .filter(IncidentRowModel::anonymous).findFirst().orElseThrow().reporterLabel());
    }

    @Test
    void administratorIncidentListOffersOnlyIdentitiesVisibleThroughIncidentRows() {
        AccountId visibleReporter = accountId("00000000-0000-0000-0000-000000000011");
        AccountId anonymousOnlyReporter = accountId("00000000-0000-0000-0000-000000000012");
        AccountId assignedResponder = accountId("00000000-0000-0000-0000-000000000013");
        accounts.create(reporter(visibleReporter));
        accounts.create(reporter(anonymousOnlyReporter));
        accounts.create(responder(assignedResponder, IncidentCategory.IT));
        IncidentLifecycle lifecycle = new IncidentLifecycle(clock);
        incidents.create(lifecycle.submit(new IncidentId(new UUID(2, 1)), visibleReporter,
                "Visible", "Description", IncidentCategory.IT, false));
        incidents.create(lifecycle.submit(new IncidentId(new UUID(2, 2)), anonymousOnlyReporter,
                "Anonymous", "Description", IncidentCategory.IT, true));
        incidents.create(lifecycle.claim(lifecycle.submit(new IncidentId(new UUID(2, 3)), visibleReporter,
                "Assigned", "Description", IncidentCategory.IT, false), assignedResponder));
        sessions.signIn(administrator());

        var result = service.administratorIncidentList(IncidentSearchCriteria.defaults(), dashboardMapper());

        assertTrue(result.isSuccess());
        var model = result.value().orElseThrow();
        assertEquals(List.of(visibleReporter), model.reporters().stream().map(option -> option.id()).toList());
        assertEquals(List.of(assignedResponder), model.responders().stream().map(option -> option.id()).toList());
        assertFalse(model.toString().contains(anonymousOnlyReporter.value().toString()));
        assertFalse(model.toString().contains("reporter-" + anonymousOnlyReporter.value()));
    }

    @Test
    void administratorIncidentViewDeniesMissingAndNonAdministratorSessions() {
        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE,
                service.administratorIncidents(IncidentSearchCriteria.defaults(), dashboardMapper())
                        .error().orElseThrow().code());
        for (Account actor : List.of(reporter(REPORTER_ID), responder(RESPONDER_ID, IncidentCategory.IT))) {
            sessions.signIn(actor);
            assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE,
                    service.administratorIncidents(IncidentSearchCriteria.defaults(), dashboardMapper())
                            .error().orElseThrow().code());
        }
    }

    private Incident dashboardIncident(long identifier, IncidentCategory category, boolean anonymous) {
        return new IncidentLifecycle(clock).submit(new IncidentId(new UUID(1, identifier)),
                REPORTER_ID, "Incident " + identifier, "Description", category, anonymous);
    }

    @Test
    void dashboardConvertsAccountReadFailureToSafeApplicationError() {
        sessions.failOnRead = true;

        ApplicationResult<ResponderDashboardModel> result = service.responderDashboard(dashboardMapper());

        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, result.error().orElseThrow().code());
        assertFalse(result.toString().contains("private path"));
    }

    private IncidentPresentationMapper dashboardMapper() {
        return new IncidentPresentationMapper(accounts, new IncidentAuthorizationPolicy(sessions),
                ZoneOffset.UTC, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
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
        private boolean failOnRead;

        void signIn(Account account) {
            current = Optional.of(account);
        }

        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return current.map(account -> new AuthenticatedSession(account.id(), NOW));
        }

        @Override
        public Optional<Account> currentAccount() {
            if (failOnRead) {
                throw new RepositoryException(StorageFailureCode.STORAGE_UNAVAILABLE, "private path");
            }
            return current;
        }
    }
}

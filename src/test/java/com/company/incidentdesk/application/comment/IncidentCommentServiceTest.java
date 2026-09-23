package com.company.incidentdesk.application.comment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.presentation.CommentModel;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.comment.CommentId;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.persistence.memory.InMemoryAccountRepository;
import com.company.incidentdesk.persistence.memory.InMemoryIncidentRepository;

class IncidentCommentServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-23T08:00:00Z");
    private static final AccountId REPORTER = id("00000000-0000-0000-0000-000000000001");
    private static final AccountId OTHER_REPORTER = id("00000000-0000-0000-0000-000000000002");
    private static final AccountId RESPONDER = id("00000000-0000-0000-0000-000000000003");
    private static final IncidentId INCIDENT = new IncidentId(UUID.fromString("10000000-0000-0000-0000-000000000001"));

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MutableSessions sessions = new MutableSessions();
    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
    private final AtomicInteger sequence = new AtomicInteger();
    private IncidentCommentService service;

    @BeforeEach
    void setUp() {
        accounts.create(account(REPORTER, "reporter", Role.REPORTER, Set.of()));
        accounts.create(account(OTHER_REPORTER, "other", Role.REPORTER, Set.of()));
        accounts.create(account(RESPONDER, "responder", Role.RESPONDER, Set.of(IncidentCategory.IT)));
        service = new IncidentCommentService(sessions, incidents, accounts,
                new IncidentAuthorizationPolicy(sessions),
                new AuditEventFactory(clock, () -> new AuditEventId(new UUID(0, sequence.incrementAndGet()))),
                clock, () -> new CommentId(new UUID(1, sequence.incrementAndGet())));
    }

    @Test
    void authorizedActorAddsAndListsCommentWithAudit() {
        createIncident(false);
        sessions.signIn(accounts.findById(RESPONDER).orElseThrow());

        assertTrue(service.add(INCIDENT, "  Investigating  ").isSuccess());

        List<CommentModel> comments = service.list(INCIDENT).value().orElseThrow();
        assertEquals(1, comments.size());
        assertEquals("responder", comments.getFirst().authorLabel());
        assertEquals("Responder", comments.getFirst().authorRoleLabel());
        assertEquals(NOW, comments.getFirst().createdAt());
        assertEquals("  Investigating  ", comments.getFirst().text());
        assertEquals(AuditAction.COMMENT_ADDED, incidents.auditEvents().getFirst().action());
    }

    @Test
    void successfulCommitPublishesSanitizedCommentEvent() {
        createIncident(false);
        sessions.signIn(accounts.findById(RESPONDER).orElseThrow());
        List<CommentAddedEvent> events = new java.util.ArrayList<>();
        service = new IncidentCommentService(sessions, incidents, accounts,
                new IncidentAuthorizationPolicy(sessions),
                new AuditEventFactory(clock, () -> new AuditEventId(new UUID(0, sequence.incrementAndGet()))),
                clock, () -> new CommentId(new UUID(1, sequence.incrementAndGet())),
                event -> events.add((CommentAddedEvent) event));

        assertTrue(service.add(INCIDENT, "Sensitive comment text").isSuccess());

        assertEquals(1, events.size());
        assertEquals(INCIDENT, events.getFirst().incidentId());
        assertEquals(RESPONDER, events.getFirst().actorId());
    }

    @Test
    void anonymousReporterIdentityIsRedactedForResponder() {
        createIncident(true);
        sessions.signIn(accounts.findById(REPORTER).orElseThrow());
        assertTrue(service.add(INCIDENT, "More context").isSuccess());
        sessions.signIn(accounts.findById(RESPONDER).orElseThrow());

        assertEquals("Anonymous reporter",
                service.list(INCIDENT).value().orElseThrow().getFirst().authorLabel());
    }

    @Test
    void postingRoleSurvivesAccountPromotion() {
        createIncident(false);
        sessions.signIn(accounts.findById(REPORTER).orElseThrow());
        assertTrue(service.add(INCIDENT, "More context").isSuccess());
        accounts.update(account(REPORTER, "reporter", Role.RESPONDER, Set.of(IncidentCategory.IT)));
        sessions.signIn(accounts.findById(RESPONDER).orElseThrow());

        assertEquals("Reporter", service.list(INCIDENT).value().orElseThrow().getFirst().authorRoleLabel());
    }

    @Test
    void inaccessibleAndWhitespaceCommentsDoNotPersist() {
        createIncident(false);
        sessions.signIn(accounts.findById(OTHER_REPORTER).orElseThrow());
        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE,
                service.add(INCIDENT, "Cannot see this").error().orElseThrow().code());
        assertEquals(ApplicationErrorCode.VALIDATION,
                service.add(INCIDENT, "  ").error().orElseThrow().code());
        assertTrue(incidents.findCommentsByIncidentId(INCIDENT).isEmpty());
        assertTrue(incidents.auditEvents().isEmpty());
    }

    private void createIncident(boolean anonymous) {
        Incident incident = new IncidentLifecycle(clock).submit(
                INCIDENT, REPORTER, "Printer", "Jammed", IncidentCategory.IT, anonymous);
        incidents.create(incident);
    }

    private static Account account(AccountId id, String name, Role role, Set<IncidentCategory> categories) {
        ResponderAccess access = role == Role.RESPONDER ? ResponderAccess.to(categories) : ResponderAccess.NONE;
        return new Account(id, name, role, AccountStatus.ENABLED, access);
    }

    private static AccountId id(String value) {
        return new AccountId(UUID.fromString(value));
    }

    private static final class MutableSessions implements SessionProvider {
        private Optional<Account> actor = Optional.empty();

        void signIn(Account account) {
            actor = Optional.of(account);
        }

        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return actor.map(account -> new AuthenticatedSession(account.id(), NOW));
        }

        @Override
        public Optional<Account> currentAccount() {
            return actor;
        }
    }
}

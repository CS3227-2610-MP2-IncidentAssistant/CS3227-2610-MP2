package com.company.incidentdesk.application.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.attachment.AttachmentLimits;
import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.comment.IncidentCommentService;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.attachment.AttachmentId;
import com.company.incidentdesk.domain.attachment.IncidentAttachment;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.comment.CommentId;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.domain.slo.SloTarget;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.domain.slo.SloTargetVersionId;
import com.company.incidentdesk.persistence.AttachmentStore;
import com.company.incidentdesk.persistence.memory.InMemoryAccountRepository;
import com.company.incidentdesk.persistence.memory.InMemoryIncidentRepository;
import com.company.incidentdesk.persistence.memory.InMemorySloConfigurationStore;

class IncidentDetailServiceTest {
    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-24T00:00:00Z");
    private static final Instant NOW = SUBMITTED_AT.plus(Duration.ofMinutes(35));
    private static final AccountId REPORTER_ID = accountId("00000000-0000-0000-0000-000000000001");
    private static final AccountId RESPONDER_ID = accountId("00000000-0000-0000-0000-000000000002");
    private static final IncidentId INCIDENT_ID = incidentId("10000000-0000-0000-0000-000000000001");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MutableSession sessions = new MutableSession();
    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
    private final InMemorySloConfigurationStore sloConfigurations = new InMemorySloConfigurationStore();
    private IncidentDetailService details;
    private IncidentCommentService comments;

    @BeforeEach
    void setUp() {
        Account reporter = account(REPORTER_ID, "secret-reporter", Role.REPORTER, ResponderAccess.NONE);
        Account responder = account(RESPONDER_ID, "responder", Role.RESPONDER,
                ResponderAccess.to(Set.of(IncidentCategory.IT)));
        accounts.create(reporter);
        accounts.create(responder);
        Incident incident = new IncidentLifecycle(Clock.fixed(SUBMITTED_AT, ZoneOffset.UTC)).submit(
                INCIDENT_ID, REPORTER_ID, "Printer outage", "Still unavailable", IncidentCategory.IT, true);
        incidents.create(incident);
        sessions.signIn(reporter);
        comments = new IncidentCommentService(sessions, incidents, accounts,
                new IncidentAuthorizationPolicy(sessions), auditFactory(), clock,
                () -> new CommentId(UUID.randomUUID()));
        assertTrue(comments.add(INCIDENT_ID, "The outage is ongoing").isSuccess());
        sloConfigurations.append(new SloTargetVersionId(new UUID(0, 1)), new SloTargetVersion(
                new SloTargetVersionId(new UUID(0, 1)), IncidentCategory.IT,
                new SloTarget(Duration.ofMinutes(30), Duration.ofHours(2), .2), SUBMITTED_AT,
                REPORTER_ID));
        AttachmentService attachments = new AttachmentService(sessions, incidents, noAttachments(),
                new IncidentAuthorizationPolicy(sessions), AttachmentLimits.DEFAULT, auditFactory(), clock,
                () -> new AttachmentId(UUID.randomUUID()));
        IncidentAuthorizationPolicy authorization = new IncidentAuthorizationPolicy(sessions);
        IncidentPresentationMapper mapper = new IncidentPresentationMapper(accounts, authorization,
                ZoneOffset.UTC, DateTimeFormatter.ISO_INSTANT);
        details = new IncidentDetailService(sessions, incidents, authorization, mapper, comments, attachments,
                sloConfigurations, clock);
        sessions.signIn(responder);
    }

    @Test
    void authorizedDetailComposesRedactedCommentsAttachmentsAndPersistedTimeSlo() {
        var result = details.detail(INCIDENT_ID);

        assertTrue(result.isSuccess());
        var model = result.value().orElseThrow();
        assertEquals("Anonymous reporter", model.summary().reporterLabel());
        assertEquals("Anonymous reporter", model.comments().getFirst().authorLabel());
        assertEquals("Overdue · Time to claim · 35m / 30m", model.slo().label());
        assertTrue(model.slo().overdue());
        assertFalse(model.toString().contains("secret-reporter"));
        assertFalse(model.toString().contains(REPORTER_ID.value().toString()));
    }

    @Test
    void inaccessibleIncidentUsesNeutralUnavailableResult() {
        sessions.signIn(account(accountId("00000000-0000-0000-0000-000000000003"), "other",
                Role.REPORTER, ResponderAccess.NONE));

        var result = details.detail(INCIDENT_ID);

        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, result.error().orElseThrow().code());
    }

    private static AttachmentStore noAttachments() {
        return new AttachmentStore() {
            @Override public List<IncidentAttachment> list(IncidentId incidentId) { return List.of(); }
            @Override public Optional<IncidentAttachment> find(AttachmentId id) { return Optional.empty(); }
            @Override public byte[] read(IncidentAttachment attachment) { throw new UnsupportedOperationException(); }
            @Override public void add(IncidentAttachment attachment, byte[] content, Incident expectedIncident,
                    Account expectedActor, AttachmentLimits limits, AuditEvent audit, AuditEvent migrationAudit) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private AuditEventFactory auditFactory() {
        return new AuditEventFactory(clock, () -> new AuditEventId(UUID.randomUUID()));
    }

    private static Account account(AccountId id, String name, Role role, ResponderAccess access) {
        return new Account(id, name, role, AccountStatus.ENABLED, access);
    }

    private static AccountId accountId(String value) { return new AccountId(UUID.fromString(value)); }
    private static IncidentId incidentId(String value) { return new IncidentId(UUID.fromString(value)); }

    private static final class MutableSession implements SessionProvider {
        private Optional<Account> actor = Optional.empty();

        void signIn(Account account) { actor = Optional.of(account); }

        @Override public Optional<AuthenticatedSession> currentSession() {
            return actor.map(account -> new AuthenticatedSession(account.id(), SUBMITTED_AT));
        }

        @Override public Optional<Account> currentAccount() { return actor; }
    }
}

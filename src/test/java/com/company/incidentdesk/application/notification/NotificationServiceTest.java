package com.company.incidentdesk.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.comment.CommentAddedEvent;
import com.company.incidentdesk.application.account.PromotionDecisionEvent;
import com.company.incidentdesk.application.account.PromotionRequestedEvent;
import com.company.incidentdesk.application.account.ResponderAccessChangedEvent;
import com.company.incidentdesk.application.event.InProcessApplicationEventBus;
import com.company.incidentdesk.application.incident.IncidentChangedEvent;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.PromotionRequestId;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.comment.CommentId;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentAction;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.persistence.memory.InMemoryAccountRepository;
import com.company.incidentdesk.persistence.memory.InMemoryIncidentRepository;

class NotificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-23T08:00:00Z");
    private static final AccountId REPORTER = accountId(1);
    private static final AccountId IT_RESPONDER = accountId(2);
    private static final AccountId HR_RESPONDER = accountId(3);
    private static final AccountId ADMIN = accountId(4);
    private static final IncidentId INCIDENT_ID = new IncidentId(new UUID(1, 1));

    private final InProcessApplicationEventBus bus = InProcessApplicationEventBus.synchronous();
    private final InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final NotificationInbox inbox = new NotificationInbox();
    private final AtomicInteger ids = new AtomicInteger();
    private NotificationService service;

    @BeforeEach
    void setUp() {
        accounts.create(account(REPORTER, Role.REPORTER, Set.of()));
        accounts.create(account(IT_RESPONDER, Role.RESPONDER, Set.of(IncidentCategory.IT)));
        accounts.create(account(HR_RESPONDER, Role.RESPONDER, Set.of(IncidentCategory.HUMAN_RELATIONS)));
        accounts.create(account(ADMIN, Role.ADMINISTRATOR, Set.of()));
        Incident incident = new IncidentLifecycle(Clock.fixed(NOW, ZoneOffset.UTC)).submit(
                INCIDENT_ID, REPORTER, "Private title", "Details", IncidentCategory.IT, true);
        incidents.create(incident);
        service = new NotificationService(bus, incidents, accounts, inbox,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> new NotificationId(new UUID(2, ids.incrementAndGet())));
    }

    @Test
    void notifiesOnlyAccountsCurrentlyAuthorizedForAnonymousIncident() {
        bus.publish(new IncidentChangedEvent(INCIDENT_ID, IncidentAction.SUBMIT, ADMIN));

        assertEquals(1, inbox.snapshot(REPORTER).notifications().size());
        assertEquals(1, inbox.snapshot(IT_RESPONDER).notifications().size());
        assertTrue(inbox.snapshot(ADMIN).notifications().isEmpty());
        assertTrue(inbox.snapshot(HR_RESPONDER).notifications().isEmpty());
        inbox.snapshot(IT_RESPONDER).notifications().forEach(notification -> {
            assertFalse(notification.message().contains("reporter"));
            assertFalse(notification.message().contains("Private title"));
        });
    }

    @Test
    void commentEventContainsNoTextAndUsesSameAuthorizationBoundary() {
        bus.publish(new CommentAddedEvent(
                INCIDENT_ID, new CommentId(new UUID(3, 1)), ADMIN));

        assertEquals(NotificationType.COMMENT,
                inbox.snapshot(IT_RESPONDER).notifications().getFirst().type());
        assertEquals(NotificationType.COMMENT,
                inbox.snapshot(REPORTER).notifications().getFirst().type());
        assertTrue(inbox.snapshot(ADMIN).notifications().isEmpty());
        assertTrue(inbox.snapshot(HR_RESPONDER).notifications().isEmpty());
    }

    @Test
    void closeUnregistersAllEventSubscriptions() {
        service.close();
        bus.publish(new IncidentChangedEvent(INCIDENT_ID, IncidentAction.SUBMIT, ADMIN));
        assertTrue(inbox.snapshot(REPORTER).notifications().isEmpty());
    }

    @Test
    void promotionRequestNotifiesAdministratorsAndDecisionNotifiesRequester() {
        PromotionRequestId requestId = new PromotionRequestId(new UUID(4, 1));

        bus.publish(new PromotionRequestedEvent(requestId, REPORTER));
        bus.publish(new PromotionDecisionEvent(REPORTER, ADMIN, true));

        assertEquals(1, inbox.snapshot(ADMIN).notifications().size());
        assertEquals(1, inbox.snapshot(REPORTER).notifications().size());
        assertTrue(inbox.snapshot(IT_RESPONDER).notifications().isEmpty());
    }

    @Test
    void categoryAccessChangeNotifiesAffectedResponder() {
        bus.publish(new ResponderAccessChangedEvent(IT_RESPONDER, ADMIN));

        assertEquals(1, inbox.snapshot(IT_RESPONDER).notifications().size());
        assertTrue(inbox.snapshot(ADMIN).notifications().isEmpty());
    }

    private static Account account(AccountId id, Role role, Set<IncidentCategory> categories) {
        ResponderAccess access = role == Role.RESPONDER
                ? ResponderAccess.to(categories) : ResponderAccess.NONE;
        return new Account(id, role.name() + id.value(), role, AccountStatus.ENABLED, access);
    }

    private static AccountId accountId(long value) {
        return new AccountId(new UUID(0, value));
    }
}

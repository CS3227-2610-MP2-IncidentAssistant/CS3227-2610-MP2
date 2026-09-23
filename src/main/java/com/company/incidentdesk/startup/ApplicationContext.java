package com.company.incidentdesk.startup;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

import com.company.incidentdesk.application.account.PasswordVerifier;
import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.event.InProcessApplicationEventBus;
import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.notification.NotificationInbox;
import com.company.incidentdesk.application.notification.NotificationService;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.session.InMemorySessionService;
import com.company.incidentdesk.application.session.SessionService;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.persistence.file.LocalApplicationStore;

/** Centrally assembled, process-wide application services. */
public final class ApplicationContext implements AutoCloseable {
    private final LocalApplicationStore store;
    private final SessionService sessions;
    private final NotificationInbox notifications;
    private final IncidentService incidents;
    private final IncidentPresentationMapper presentationMapper;
    private final NotificationService notificationService;

    private ApplicationContext(
            LocalApplicationStore store,
            SessionService sessions,
            NotificationInbox notifications,
            IncidentService incidents,
            IncidentPresentationMapper presentationMapper,
            NotificationService notificationService) {
        this.store = store;
        this.sessions = sessions;
        this.notifications = notifications;
        this.incidents = incidents;
        this.presentationMapper = presentationMapper;
        this.notificationService = notificationService;
    }

    public static ApplicationContext openDefault(PasswordVerifier passwordVerifier) {
        LocalApplicationStore store = LocalApplicationStore.openDefault();
        try {
            return create(store, passwordVerifier, Clock.systemUTC());
        } catch (RuntimeException exception) {
            store.close();
            throw exception;
        }
    }

    static ApplicationContext create(
            LocalApplicationStore store,
            PasswordVerifier passwordVerifier,
            Clock clock) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(passwordVerifier, "passwordVerifier");
        Objects.requireNonNull(clock, "clock");
        SessionService sessions = new InMemorySessionService(store, passwordVerifier, clock);
        IncidentAuthorizationPolicy authorization = new IncidentAuthorizationPolicy(sessions);
        InProcessApplicationEventBus events = InProcessApplicationEventBus.onJavaFxThread(
                ApplicationContext::reportSubscriberFailure);
        NotificationInbox notifications = new NotificationInbox();
        NotificationService notificationService = new NotificationService(
                events, store, store, notifications, clock);
        IncidentService incidents = new IncidentService(
                sessions,
                store,
                store,
                authorization,
                new IncidentLifecycle(clock),
                new AuditEventFactory(clock, () -> new AuditEventId(UUID.randomUUID())),
                () -> new IncidentId(UUID.randomUUID()),
                events::publish);
        IncidentPresentationMapper mapper = new IncidentPresentationMapper(
                store, authorization, ZoneId.systemDefault(), DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm"));
        return new ApplicationContext(store, sessions, notifications, incidents, mapper, notificationService);
    }

    public SessionService sessions() {
        return sessions;
    }

    public NotificationInbox notifications() {
        return notifications;
    }

    public IncidentService incidents() {
        return incidents;
    }

    public IncidentPresentationMapper presentationMapper() {
        return presentationMapper;
    }

    private static void reportSubscriberFailure(RuntimeException exception) {
        System.err.println("Application event subscriber failed: " + exception.getClass().getSimpleName());
    }

    @Override
    public void close() {
        notificationService.close();
        sessions.logout();
        store.close();
    }
}

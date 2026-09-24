package com.company.incidentdesk.startup;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

import com.company.incidentdesk.application.account.PasswordVerifier;
import com.company.incidentdesk.application.account.AccountRegistrationService;
import com.company.incidentdesk.application.account.AccountDirectoryService;
import com.company.incidentdesk.application.account.AccountDeletionService;
import com.company.incidentdesk.application.account.AccountPasswordService;
import com.company.incidentdesk.application.account.AccountPasswordResetService;
import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.event.InProcessApplicationEventBus;
import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.notification.NotificationInbox;
import com.company.incidentdesk.application.notification.NotificationService;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.session.InMemorySessionService;
import com.company.incidentdesk.application.session.SessionService;
import com.company.incidentdesk.application.slo.SloConfigurationService;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.domain.slo.SloTargetVersionId;
import com.company.incidentdesk.persistence.file.LocalApplicationStore;

/** Centrally assembled, process-wide application services. */
public final class ApplicationContext implements AutoCloseable {
    private final LocalApplicationStore store;
    private final SessionService sessions;
    private final NotificationInbox notifications;
    private final IncidentService incidents;
    private final IncidentPresentationMapper presentationMapper;
    private final NotificationService notificationService;
    private final AccountRegistrationService registrations;
    private final AccountDirectoryService accountDirectory;
    private final AccountDeletionService accountDeletion;
    private final AccountPasswordService passwords;
    private final AccountPasswordResetService passwordResets;
    private final SloConfigurationService sloConfigurations;

    private ApplicationContext(
            LocalApplicationStore store,
            SessionService sessions,
            NotificationInbox notifications,
            IncidentService incidents,
            IncidentPresentationMapper presentationMapper,
            NotificationService notificationService,
            AccountRegistrationService registrations,
            AccountDirectoryService accountDirectory,
            AccountDeletionService accountDeletion,
            AccountPasswordService passwords,
            AccountPasswordResetService passwordResets,
            SloConfigurationService sloConfigurations) {
        this.store = store;
        this.sessions = sessions;
        this.notifications = notifications;
        this.incidents = incidents;
        this.presentationMapper = presentationMapper;
        this.notificationService = notificationService;
        this.registrations = registrations;
        this.accountDirectory = accountDirectory;
        this.accountDeletion = accountDeletion;
        this.passwords = passwords;
        this.passwordResets = passwordResets;
        this.sloConfigurations = sloConfigurations;
    }

    public static ApplicationContext openDefault() {
        LocalApplicationStore store = LocalApplicationStore.openDefault();
        try {
            Clock clock = Clock.systemUTC();
            AccountRegistrationService registrations = new AccountRegistrationService(store, clock);
            return create(store, registrations, registrations, clock);
        } catch (RuntimeException exception) {
            store.close();
            throw exception;
        }
    }

    static ApplicationContext create(
            LocalApplicationStore store,
            PasswordVerifier passwordVerifier,
            AccountRegistrationService registrations,
            Clock clock) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(passwordVerifier, "passwordVerifier");
        Objects.requireNonNull(clock, "clock");
        SessionService sessions = new InMemorySessionService(store, passwordVerifier, clock);
        IncidentAuthorizationPolicy authorization = new IncidentAuthorizationPolicy(sessions);
        AccountAuthorizationPolicy accountAuthorization = new AccountAuthorizationPolicy(sessions);
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
        AccountDirectoryService accountDirectory = new AccountDirectoryService(accountAuthorization, store);
        AccountDeletionService accountDeletion = new AccountDeletionService(sessions, accountAuthorization, store,
                new AuditEventFactory(clock, () -> new AuditEventId(UUID.randomUUID())));
        AccountPasswordService passwords = new AccountPasswordService(sessions, registrations, store,
                new AuditEventFactory(clock, () -> new AuditEventId(UUID.randomUUID())));
        AccountPasswordResetService passwordResets = new AccountPasswordResetService(sessions,
                accountAuthorization, registrations, store,
                new AuditEventFactory(clock, () -> new AuditEventId(UUID.randomUUID())), clock);
        SloConfigurationService sloConfigurations = new SloConfigurationService(
                sessions, accountAuthorization, store.sloConfigurationStore(),
                new AuditEventFactory(clock, () -> new AuditEventId(UUID.randomUUID())), clock,
                () -> new SloTargetVersionId(UUID.randomUUID()));
        return new ApplicationContext(store, sessions, notifications, incidents, mapper, notificationService,
                Objects.requireNonNull(registrations, "registrations"), accountDirectory, accountDeletion, passwords,
                passwordResets,
                sloConfigurations);
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

    public AccountRegistrationService registrations() {
        return registrations;
    }

    public AccountDirectoryService accountDirectory() {
        return accountDirectory;
    }

    public AccountDeletionService accountDeletion() {
        return accountDeletion;
    }

    public AccountPasswordService passwords() {
        return passwords;
    }

    public AccountPasswordResetService passwordResets() { return passwordResets; }

    public SloConfigurationService sloConfigurations() {
        return sloConfigurations;
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

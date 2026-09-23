package com.company.incidentdesk.application.notification;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.company.incidentdesk.application.account.PromotionDecisionEvent;
import com.company.incidentdesk.application.account.ResponderAccessChangedEvent;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.comment.CommentAddedEvent;
import com.company.incidentdesk.application.event.ApplicationEventBus;
import com.company.incidentdesk.application.event.Subscription;
import com.company.incidentdesk.application.incident.IncidentChangedEvent;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentAction;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.persistence.AccountRepository;
import com.company.incidentdesk.persistence.IncidentRepository;

/** Converts post-commit events into authorized, recipient-specific notifications. */
public final class NotificationService implements AutoCloseable {
    private final IncidentRepository incidents;
    private final AccountRepository accounts;
    private final NotificationInbox inbox;
    private final Clock clock;
    private final Supplier<NotificationId> identifiers;
    private final List<Subscription> subscriptions = new ArrayList<>();

    public NotificationService(
            ApplicationEventBus eventBus,
            IncidentRepository incidents,
            AccountRepository accounts,
            NotificationInbox inbox,
            Clock clock) {
        this(eventBus, incidents, accounts, inbox, clock,
                () -> new NotificationId(UUID.randomUUID()));
    }

    public NotificationService(
            ApplicationEventBus eventBus,
            IncidentRepository incidents,
            AccountRepository accounts,
            NotificationInbox inbox,
            Clock clock,
            Supplier<NotificationId> identifiers) {
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
        ApplicationEventBus requiredBus = Objects.requireNonNull(eventBus, "eventBus");
        subscriptions.add(requiredBus.subscribe(IncidentChangedEvent.class, this::onIncidentChanged));
        subscriptions.add(requiredBus.subscribe(CommentAddedEvent.class, this::onCommentAdded));
        subscriptions.add(requiredBus.subscribe(PromotionDecisionEvent.class, this::onPromotionDecision));
        subscriptions.add(requiredBus.subscribe(ResponderAccessChangedEvent.class, this::onAccessChanged));
    }

    private void onIncidentChanged(IncidentChangedEvent event) {
        incidents.findById(event.incidentId()).ifPresent(incident -> notifyIncidentRecipients(
                incident, event.actorId(), NotificationType.INCIDENT, incidentMessage(event.action())));
    }

    private void onCommentAdded(CommentAddedEvent event) {
        incidents.findById(event.incidentId()).ifPresent(incident -> notifyIncidentRecipients(
                incident, event.actorId(), NotificationType.COMMENT,
                "A comment was added to an incident."));
    }

    private void onPromotionDecision(PromotionDecisionEvent event) {
        accounts.findById(event.accountId()).filter(Account::isEnabled)
                .filter(account -> !account.id().equals(event.actorId())).ifPresent(account -> add(
                account.id(), NotificationType.ACCOUNT, Optional.empty(),
                event.approved() ? "Your responder request was approved."
                        : "Your responder request was not approved."));
    }

    private void onAccessChanged(ResponderAccessChangedEvent event) {
        accounts.findById(event.accountId()).filter(Account::isEnabled)
                .filter(account -> !account.id().equals(event.actorId())).ifPresent(account -> add(
                account.id(), NotificationType.ACCOUNT, Optional.empty(),
                "Your responder category access changed."));
    }

    private void notifyIncidentRecipients(
            Incident incident,
            AccountId actorId,
            NotificationType type,
            String message) {
        accounts.findAll().stream()
                .filter(Account::isEnabled)
                .filter(account -> !account.id().equals(actorId))
                .filter(account -> IncidentAuthorizationPolicy.canViewIncident(account, incident))
                .forEach(account -> add(account.id(), type, Optional.of(incident.id()), message));
    }

    private void add(
            AccountId recipientId,
            NotificationType type,
            Optional<IncidentId> incidentId,
            String message) {
        inbox.add(new Notification(
                Objects.requireNonNull(identifiers.get(), "generated notification identifier"),
                recipientId, type, incidentId, message, clock.instant(), 1));
    }

    private static String incidentMessage(IncidentAction action) {
        return switch (action) {
        case SAVE_DRAFT -> "An incident draft was saved.";
        case SUBMIT -> "An incident was submitted.";
        case EDIT -> "An incident was updated.";
        case WITHDRAW -> "An incident was withdrawn.";
        case CLAIM -> "An incident was claimed.";
        case ASSIGN -> "An incident was assigned.";
        case REASSIGN -> "An incident was reassigned.";
        case RESOLVE -> "An incident was resolved.";
        case HANDOFF -> "An incident returned to its category queue.";
        case REOPEN -> "An incident was reopened.";
        };
    }

    @Override
    public void close() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }
}

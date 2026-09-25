package com.company.incidentdesk.application.account;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.event.ApplicationEvent;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.result.OperationCompleted;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditChange;
import com.company.incidentdesk.domain.audit.AuditChangeField;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.persistence.AccountRepository;
import com.company.incidentdesk.persistence.AuditedMutation;
import com.company.incidentdesk.persistence.RepositoryException;

/** Administrator-only responder category-access updates. */
public final class ResponderAccessService {
    private final SessionProvider sessions;
    private final AccountAuthorizationPolicy authorization;
    private final AccountRepository accounts;
    private final PromotionWorkflowStore store;
    private final AuditEventFactory auditEvents;
    private final Consumer<ApplicationEvent> eventPublisher;

    public ResponderAccessService(SessionProvider sessions, AccountAuthorizationPolicy authorization,
            AccountRepository accounts, PromotionWorkflowStore store, AuditEventFactory auditEvents,
            Consumer<ApplicationEvent> eventPublisher) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.store = Objects.requireNonNull(store, "store");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public ApplicationResult<OperationCompleted> changeCategories(
            AccountId responderId, Set<IncidentCategory> categories) {
        Objects.requireNonNull(responderId, "responderId");
        Objects.requireNonNull(categories, "categories");
        Optional<Account> located = authorizedResponder(responderId);
        if (located.isEmpty()) {
            return unavailable();
        }
        Account actor = sessions.currentAccount().orElseThrow();
        Account current = located.orElseThrow();
        Account updated = new Account(current.id(), current.loginName(), current.role(), current.status(),
                ResponderAccess.to(categories));
        String before = format(current.responderAccess().categories());
        String after = format(updated.responderAccess().categories());
        var audit = auditEvents.create(actor, AuditActorVisibility.STANDARD,
                AuditAction.RESPONDER_ACCESS_CHANGED,
                new AuditTarget(AuditTargetType.ACCOUNT, responderId.value().toString()), AuditOutcome.SUCCESS,
                List.of(AuditChange.changed(AuditChangeField.RESPONDER_CATEGORIES, before, after)), Optional.empty());
        try {
            store.commitPromotion(new AuditedMutation<>(new PromotionWorkflowMutation(
                    Optional.empty(), Optional.of(updated)), audit));
        } catch (RepositoryException exception) {
            return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.PERSISTENCE_FAILURE));
        }
        eventPublisher.accept(new ResponderAccessChangedEvent(responderId, actor.id()));
        return ApplicationResult.success(OperationCompleted.INSTANCE);
    }

    private Optional<Account> authorizedResponder(AccountId responderId) {
        if (sessions.currentAccount().isEmpty() || !authorization.authorizeCategoryAccessChange().isAllowed()) {
            return Optional.empty();
        }
        return accounts.findById(responderId)
                .filter(Account::isEnabled).filter(account -> account.role() == Role.RESPONDER);
    }

    private static String format(Set<IncidentCategory> categories) {
        return categories.isEmpty() ? "none"
                : categories.stream().sorted().map(Enum::name).reduce((left, right) -> left + "," + right)
                        .orElseThrow();
    }

    private static <T> ApplicationResult<T> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }
}

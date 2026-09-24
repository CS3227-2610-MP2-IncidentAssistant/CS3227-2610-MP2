package com.company.incidentdesk.application.account;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.result.OperationCompleted;
import com.company.incidentdesk.application.session.SessionService;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditChange;
import com.company.incidentdesk.domain.audit.AuditChangeField;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** Tombstones accounts without breaking retained historical relationships. */
public final class AccountDeletionService {
    private final SessionService sessions;
    private final AccountAuthorizationPolicy authorization;
    private final AccountDeletionStore store;
    private final AuditEventFactory auditEvents;

    public AccountDeletionService(SessionService sessions, AccountAuthorizationPolicy authorization,
            AccountDeletionStore store, AuditEventFactory auditEvents) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.store = Objects.requireNonNull(store, "store");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
    }

    /** Tombstones another account and removes its authentication credential. */
    public ApplicationResult<OperationCompleted> delete(AccountId accountId) {
        Objects.requireNonNull(accountId, "accountId");
        Optional<Account> actor = sessions.currentAccount();
        if (!authorization.authorizeDeleteOrResetAccount().isAllowed() || actor.isEmpty()) {
            return unavailable();
        }
        if (actor.orElseThrow().id().equals(accountId)) {
            return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.INVALID_STATE));
        }
        Optional<Account> target = store.findById(accountId);
        if (target.isEmpty()) {
            return unavailable();
        }
        if (target.orElseThrow().isDeleted()) {
            return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.INVALID_STATE));
        }

        Account tombstone = target.orElseThrow().tombstone();
        AuditEvent audit = auditEvents.create(actor.orElseThrow(), AuditActorVisibility.STANDARD,
                AuditAction.ACCOUNT_DELETED,
                new AuditTarget(AuditTargetType.ACCOUNT, accountId.value().toString()), AuditOutcome.SUCCESS,
                List.of(AuditChange.changed(AuditChangeField.ACCOUNT_STATUS,
                        target.orElseThrow().status().name(), tombstone.status().name())), Optional.empty());
        try {
            store.delete(tombstone, audit);
        } catch (RepositoryException exception) {
            ApplicationErrorCode code = exception.code() == StorageFailureCode.CORRUPT_DATA
                    ? ApplicationErrorCode.CORRUPT_DATA : ApplicationErrorCode.PERSISTENCE_FAILURE;
            return ApplicationResult.failure(ApplicationError.of(code));
        }
        return ApplicationResult.success(OperationCompleted.INSTANCE);
    }

    /** Returns whether the current administrator may delete the target account. */
    public boolean canDelete(AccountId accountId) {
        Objects.requireNonNull(accountId, "accountId");
        Optional<Account> actor = sessions.currentAccount();
        if (!authorization.authorizeDeleteOrResetAccount().isAllowed() || actor.isEmpty()
                || actor.orElseThrow().id().equals(accountId)) {
            return false;
        }
        return store.findById(accountId).filter(account -> !account.isDeleted()).isPresent();
    }

    private static <T> ApplicationResult<T> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }
}

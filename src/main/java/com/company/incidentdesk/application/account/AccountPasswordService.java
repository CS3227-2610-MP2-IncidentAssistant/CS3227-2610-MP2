package com.company.incidentdesk.application.account;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.persistence.RepositoryException;

/** Replaces the active account's credential after re-authentication. */
public final class AccountPasswordService implements PasswordChanger {
    private final SessionProvider sessions;
    private final AccountRegistrationService credentials;
    private final PasswordChangeStore store;
    private final AuditEventFactory auditEvents;

    public AccountPasswordService(SessionProvider sessions, AccountRegistrationService credentials,
            PasswordChangeStore store, AuditEventFactory auditEvents) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.store = Objects.requireNonNull(store, "store");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
    }

    @Override
    public PasswordChangeResult changePassword(char[] currentPassword, char[] newPassword, char[] confirmation) {
        Objects.requireNonNull(currentPassword, "currentPassword");
        Objects.requireNonNull(newPassword, "newPassword");
        Objects.requireNonNull(confirmation, "confirmation");
        char[] current = Arrays.copyOf(currentPassword, currentPassword.length);
        char[] replacement = Arrays.copyOf(newPassword, newPassword.length);
        char[] repeated = Arrays.copyOf(confirmation, confirmation.length);
        try {
            Optional<Account> actor = sessions.currentAccount();
            if (actor.isEmpty() || !credentials.verify(actor.orElseThrow().id(), current)) {
                return PasswordChangeResult.INVALID_CURRENT_PASSWORD;
            }
            if (replacement.length == 0) {
                return PasswordChangeResult.INVALID_NEW_PASSWORD;
            }
            if (!Arrays.equals(replacement, repeated)) {
                return PasswordChangeResult.NEW_PASSWORD_MISMATCH;
            }
            Account account = actor.orElseThrow();
            boolean completesReset = credentials.isTemporaryAndValid(account.id());
            var audit = auditEvents.create(account, AuditActorVisibility.STANDARD,
                    completesReset ? AuditAction.PASSWORD_RESET_COMPLETED : AuditAction.PASSWORD_CHANGED,
                    new AuditTarget(AuditTargetType.ACCOUNT, account.id().value().toString()), AuditOutcome.SUCCESS,
                    List.of(), Optional.empty());
            store.changePassword(account.id(), credentials.hash(replacement), audit);
            return PasswordChangeResult.CHANGED;
        } catch (RepositoryException exception) {
            return PasswordChangeResult.FAILED;
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(replacement, '\0');
            Arrays.fill(repeated, '\0');
        }
    }
}

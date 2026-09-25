package com.company.incidentdesk.application.audit;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.company.incidentdesk.application.account.AccountLookup;
import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.persistence.AuditQuery;
import com.company.incidentdesk.persistence.AuditRepository;
import com.company.incidentdesk.persistence.AuditSortDirection;
import com.company.incidentdesk.persistence.RepositoryException;

/** Supplies administrator-authorized, privacy-safe application audit history. */
public final class AuditLogService {
    private final AccountAuthorizationPolicy authorization;
    private final AuditRepository audits;
    private final AuditActorLabelResolver actorLabels;
    private final AccountLookup accounts;

    public AuditLogService(
            AccountAuthorizationPolicy authorization,
            AuditRepository audits,
            AuditActorLabelResolver actorLabels,
            AccountLookup accounts) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.actorLabels = Objects.requireNonNull(actorLabels, "actorLabels");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    public ApplicationResult<List<AuditLogEntry>> listAuditLog() {
        if (!authorization.authorizeAuditLog().isAllowed()) {
            return unavailable();
        }
        try {
            List<AuditEvent> events = audits.find(AuditQuery.all(), AuditSortDirection.NEWEST_FIRST);
            if (!authorization.authorizeAuditLog().isAllowed()) {
                return unavailable();
            }
            return ApplicationResult.success(events.stream().map(this::toEntry).toList());
        } catch (RepositoryException exception) {
            return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.PERSISTENCE_FAILURE));
        }
    }

    private AuditLogEntry toEntry(AuditEvent event) {
        return new AuditLogEntry(
                event.id().value().toString(),
                event.occurredAt(),
                actorLabels.resolve(event.actor()),
                event.actor().role(),
                event.action(),
                event.target().type(),
                event.target().identifier(),
                event.outcome(),
                eventDescription(event),
                details(event));
    }

    private String eventDescription(AuditEvent event) {
        String target = event.target().type().name().toLowerCase().replace('_', ' ')
                + " " + event.target().identifier();
        if (event.target().type() == AuditTargetType.ACCOUNT) {
            target += accountUsername(event.target().identifier());
        }
        String action = event.action().name().toLowerCase().replace('_', ' ');
        String targetPrefix = event.target().type().name().toLowerCase() + "_";
        if (event.action().name().toLowerCase().startsWith(targetPrefix)) {
            action = action.substring(targetPrefix.length()).replace('_', ' ');
        }
        return target + " " + action + ".";
    }

    private String accountUsername(String identifier) {
        try {
            var accountId = new AccountId(UUID.fromString(identifier));
            return accounts.findById(accountId)
                    .map(account -> " with username " + account.loginName())
                    .orElse("");
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String details(AuditEvent event) {
        String changes = event.changes().stream()
                .map(change -> change.field().name() + ": "
                        + change.beforeValue().orElse("—") + " → " + change.afterValue().orElse("—"))
                .collect(Collectors.joining("; "));
        String evidence = event.evidenceReference()
                .map(reference -> reference.type().name() + ": " + reference.identifier())
                .orElse("");
        return List.of(changes, evidence).stream()
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining("; "));
    }

    private static ApplicationResult<List<AuditLogEntry>> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }
}

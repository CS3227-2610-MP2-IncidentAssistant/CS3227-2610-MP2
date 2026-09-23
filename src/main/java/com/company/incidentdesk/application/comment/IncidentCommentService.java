package com.company.incidentdesk.application.comment;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.event.ApplicationEventPublisher;
import com.company.incidentdesk.application.incident.IncidentMutation;
import com.company.incidentdesk.application.presentation.CommentModel;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.application.validation.RequiredTextValidator;
import com.company.incidentdesk.application.validation.ValidationField;
import com.company.incidentdesk.application.validation.ValidationResult;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEvidenceReference;
import com.company.incidentdesk.domain.audit.AuditEvidenceType;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.domain.comment.CommentId;
import com.company.incidentdesk.domain.comment.CommentType;
import com.company.incidentdesk.domain.comment.IncidentComment;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.persistence.AccountRepository;
import com.company.incidentdesk.persistence.AuditedMutation;
import com.company.incidentdesk.persistence.IncidentStore;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** Authorized application boundary for reading and adding incident comments. */
public final class IncidentCommentService {
    private static final ValidationField COMMENT_TEXT = new ValidationField("comment.text");

    private final SessionProvider sessions;
    private final IncidentStore incidents;
    private final AccountRepository accounts;
    private final IncidentAuthorizationPolicy authorization;
    private final AuditEventFactory auditEvents;
    private final Clock clock;
    private final Supplier<CommentId> identifiers;
    private final ApplicationEventPublisher eventPublisher;
    private final RequiredTextValidator requiredText = new RequiredTextValidator();

    public IncidentCommentService(
            SessionProvider sessions,
            IncidentStore incidents,
            AccountRepository accounts,
            IncidentAuthorizationPolicy authorization,
            AuditEventFactory auditEvents,
            Clock clock,
            Supplier<CommentId> identifiers) {
        this(sessions, incidents, accounts, authorization, auditEvents, clock, identifiers,
                ApplicationEventPublisher.NO_OP);
    }

    public IncidentCommentService(
            SessionProvider sessions,
            IncidentStore incidents,
            AccountRepository accounts,
            IncidentAuthorizationPolicy authorization,
            AuditEventFactory auditEvents,
            Clock clock,
            Supplier<CommentId> identifiers,
            ApplicationEventPublisher eventPublisher) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public ApplicationResult<List<CommentModel>> list(IncidentId incidentId) {
        Objects.requireNonNull(incidentId, "incidentId");
        try {
            Optional<Incident> incident = incidents.findById(incidentId);
            if (incident.isEmpty() || !authorization.authorizeViewComments(incident.orElseThrow()).isAllowed()) {
                return unavailable();
            }
            Incident visibleIncident = incident.orElseThrow();
            List<CommentModel> comments = incidents.findCommentsByIncidentId(incidentId).stream()
                    .sorted(Comparator.comparing(IncidentComment::createdAt)
                            .thenComparing(comment -> comment.id().value()))
                    .map(comment -> toModel(visibleIncident, comment))
                    .toList();
            return ApplicationResult.success(comments);
        } catch (RepositoryException exception) {
            return storageFailure(exception);
        }
    }

    public ApplicationResult<CommentModel> add(IncidentId incidentId, String text) {
        ValidationResult validation = requiredText.validate(COMMENT_TEXT, text);
        if (!validation.isValid()) {
            return ApplicationResult.failure(ApplicationError.validation(validation));
        }
        Optional<Account> actor = sessions.currentAccount().filter(Account::isEnabled);
        if (actor.isEmpty()) {
            return unavailable();
        }
        try {
            Optional<Incident> incident = incidents.findById(Objects.requireNonNull(incidentId, "incidentId"));
            if (incident.isEmpty() || !authorization.authorizeComment(incident.orElseThrow()).isAllowed()) {
                return unavailable();
            }
            Incident visibleIncident = incident.orElseThrow();
            IncidentComment comment = new IncidentComment(
                    Objects.requireNonNull(identifiers.get(), "generated comment identifier"),
                    incidentId, actor.orElseThrow().id(), actor.orElseThrow().role(),
                    clock.instant(), CommentType.ORDINARY, text);
            AuditEvent audit = auditEvents.create(
                    actor.orElseThrow(), visibility(actor.orElseThrow(), visibleIncident), AuditAction.COMMENT_ADDED,
                    new AuditTarget(AuditTargetType.INCIDENT, incidentId.value().toString()), AuditOutcome.SUCCESS,
                    List.of(), Optional.of(new AuditEvidenceReference(
                            AuditEvidenceType.COMMENT, comment.id().value().toString())));
            incidents.commit(new AuditedMutation<>(IncidentMutation.comment(visibleIncident, comment), audit));
            eventPublisher.publish(new CommentAddedEvent(
                    incidentId, comment.id(), actor.orElseThrow().id()));
            return ApplicationResult.success(toModel(visibleIncident, comment));
        } catch (RepositoryException exception) {
            return storageFailure(exception);
        }
    }

    private CommentModel toModel(Incident incident, IncidentComment comment) {
        String roleLabel = switch (comment.authorRole()) {
        case REPORTER -> "Reporter";
        case RESPONDER -> "Responder";
        case ADMINISTRATOR -> "Administrator";
        };
        return new CommentModel(authorLabel(incident, comment), roleLabel, comment.text(), comment.createdAt());
    }

    private String authorLabel(Incident incident, IncidentComment comment) {
        if (incident.anonymous() && comment.authorId().equals(incident.reporterId())) {
            return "Anonymous reporter";
        }
        return accounts.findById(comment.authorId()).map(Account::loginName).orElse("Deleted account");
    }

    private static AuditActorVisibility visibility(Account actor, Incident incident) {
        return incident.anonymous() && actor.id().equals(incident.reporterId())
                ? AuditActorVisibility.ANONYMOUS_REPORTER
                : AuditActorVisibility.STANDARD;
    }

    private static <T> ApplicationResult<T> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }

    private static <T> ApplicationResult<T> storageFailure(RepositoryException exception) {
        ApplicationErrorCode code = exception.code() == StorageFailureCode.CORRUPT_DATA
                ? ApplicationErrorCode.CORRUPT_DATA : ApplicationErrorCode.PERSISTENCE_FAILURE;
        return ApplicationResult.failure(ApplicationError.of(code));
    }
}

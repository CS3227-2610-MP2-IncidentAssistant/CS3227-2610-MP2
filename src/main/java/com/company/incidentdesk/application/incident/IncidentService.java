package com.company.incidentdesk.application.incident;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.AuthorizationDecision;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.presentation.ResponderDashboardModel;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.application.validation.RequiredTextValidator;
import com.company.incidentdesk.application.validation.ValidationError;
import com.company.incidentdesk.application.validation.ValidationErrorCode;
import com.company.incidentdesk.application.validation.ValidationField;
import com.company.incidentdesk.application.validation.ValidationResult;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditChange;
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
import com.company.incidentdesk.domain.incident.IncidentAction;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.domain.incident.InvalidIncidentTransitionException;
import com.company.incidentdesk.domain.incident.ReopenTransition;
import com.company.incidentdesk.persistence.AccountRepository;
import com.company.incidentdesk.persistence.AuditedMutation;
import com.company.incidentdesk.persistence.IncidentQuery;
import com.company.incidentdesk.persistence.IncidentStore;
import com.company.incidentdesk.persistence.IncidentSort;
import com.company.incidentdesk.persistence.IncidentSearchCriteria;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** Shared application entry point for all incident reads and lifecycle operations. */
public final class IncidentService {
    private static final ValidationField TITLE = new ValidationField("incident.title");
    private static final ValidationField DESCRIPTION = new ValidationField("incident.description");
    private static final ValidationField CATEGORY = new ValidationField("incident.category");
    private static final ValidationField RESOLUTION_REMARKS = new ValidationField("incident.resolutionRemarks");
    private static final ValidationField REOPEN_EXPLANATION = new ValidationField("incident.reopenExplanation");

    private final SessionProvider sessionProvider;
    private final IncidentStore incidentStore;
    private final AccountRepository accountRepository;
    private final IncidentAuthorizationPolicy authorizationPolicy;
    private final IncidentLifecycle lifecycle;
    private final AuditEventFactory auditEventFactory;
    private final Supplier<IncidentId> incidentIdentifierGenerator;
    private final Supplier<CommentId> commentIdentifierGenerator;
    private final IncidentEventPublisher eventPublisher;
    private final RequiredTextValidator requiredTextValidator = new RequiredTextValidator();

    public IncidentService(
            SessionProvider sessionProvider,
            IncidentStore incidentStore,
            AccountRepository accountRepository,
            IncidentAuthorizationPolicy authorizationPolicy,
            IncidentLifecycle lifecycle,
            AuditEventFactory auditEventFactory,
            Supplier<IncidentId> incidentIdentifierGenerator,
            IncidentEventPublisher eventPublisher) {
        this(sessionProvider, incidentStore, accountRepository, authorizationPolicy, lifecycle,
                auditEventFactory, incidentIdentifierGenerator,
                () -> new CommentId(UUID.randomUUID()), eventPublisher);
    }

    public IncidentService(
            SessionProvider sessionProvider,
            IncidentStore incidentStore,
            AccountRepository accountRepository,
            IncidentAuthorizationPolicy authorizationPolicy,
            IncidentLifecycle lifecycle,
            AuditEventFactory auditEventFactory,
            Supplier<IncidentId> incidentIdentifierGenerator,
            Supplier<CommentId> commentIdentifierGenerator,
            IncidentEventPublisher eventPublisher) {
        this.sessionProvider = Objects.requireNonNull(sessionProvider, "sessionProvider");
        this.incidentStore = Objects.requireNonNull(incidentStore, "incidentStore");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy, "authorizationPolicy");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.auditEventFactory = Objects.requireNonNull(auditEventFactory, "auditEventFactory");
        this.incidentIdentifierGenerator = Objects.requireNonNull(
                incidentIdentifierGenerator,
                "incidentIdentifierGenerator");
        this.commentIdentifierGenerator = Objects.requireNonNull(
                commentIdentifierGenerator,
                "commentIdentifierGenerator");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public ApplicationResult<IncidentView> saveDraft(
            String title,
            String description,
            IncidentCategory category,
            boolean anonymous) {
        Optional<Account> actor = currentActor();
        if (actor.isEmpty() || !authorizationPolicy.authorizeCreate(actor.orElseThrow().id()).isAllowed()) {
            return unavailable();
        }
        ValidationResult validation = validateCategory(category);
        if (!validation.isValid()) {
            return validationFailure(validation);
        }
        Incident incident = lifecycle.saveDraft(
                generatedIncidentId(),
                actor.orElseThrow().id(),
                safeText(title),
                safeText(description),
                category,
                anonymous);
        return commit(
                actor.orElseThrow(),
                IncidentMutation.create(incident),
                IncidentAction.SAVE_DRAFT,
                AuditAction.DRAFT_CREATED,
                IncidentAuditChanges.initialValues(incident),
                Optional.empty());
    }

    public ApplicationResult<IncidentView> submit(
            String title,
            String description,
            IncidentCategory category,
            boolean anonymous) {
        Optional<Account> actor = currentActor();
        if (actor.isEmpty() || !authorizationPolicy.authorizeSubmit(actor.orElseThrow().id()).isAllowed()) {
            return unavailable();
        }
        ValidationResult validation = validateSubmittedContent(title, description, category);
        if (!validation.isValid()) {
            return validationFailure(validation);
        }
        Incident incident = lifecycle.submit(
                generatedIncidentId(),
                actor.orElseThrow().id(),
                title,
                description,
                category,
                anonymous);
        return commit(
                actor.orElseThrow(),
                IncidentMutation.create(incident),
                IncidentAction.SUBMIT,
                AuditAction.INCIDENT_CREATED,
                IncidentAuditChanges.initialValues(incident),
                Optional.empty());
    }

    public ApplicationResult<IncidentView> submitDraft(IncidentId incidentId) {
        return mutateExisting(incidentId, (actor, incident) -> {
            if (!authorizationPolicy.authorizeSubmit(incident.reporterId()).isAllowed()) {
                return unavailable();
            }
            ValidationResult validation = validateSubmittedContent(
                    incident.title(), incident.description(), incident.category());
            if (!validation.isValid()) {
                return validationFailure(validation);
            }
            Incident submitted = lifecycle.submit(incident);
            return commit(
                    actor,
                    IncidentMutation.update(submitted),
                    IncidentAction.SUBMIT,
                    AuditAction.INCIDENT_SUBMITTED,
                    IncidentAuditChanges.statusChange(incident, submitted),
                    Optional.empty());
        });
    }

    public ApplicationResult<IncidentView> edit(
            IncidentId incidentId,
            String title,
            String description,
            IncidentCategory category,
            boolean anonymous) {
        return mutateExisting(incidentId, (actor, incident) -> {
            if (!authorizationPolicy.authorizeEdit(incident, anonymous).isAllowed()) {
                return unavailable();
            }
            ValidationResult validation = incident.status() == IncidentStatus.DRAFT
                    ? validateCategory(category)
                    : validateSubmittedContent(title, description, category);
            if (!validation.isValid()) {
                return validationFailure(validation);
            }
            Incident edited = lifecycle.edit(incident, safeText(title), safeText(description), category, anonymous);
            return commit(
                    actor,
                    IncidentMutation.update(edited),
                    IncidentAction.EDIT,
                    AuditAction.INCIDENT_EDITED,
                    IncidentAuditChanges.editChanges(incident, edited),
                    Optional.empty());
        });
    }

    public ApplicationResult<IncidentView> withdraw(IncidentId incidentId) {
        return transition(
                incidentId,
                authorizationPolicy::authorizeWithdraw,
                lifecycle::withdraw,
                IncidentAction.WITHDRAW,
                AuditAction.INCIDENT_WITHDRAWN);
    }

    public ApplicationResult<IncidentView> claim(IncidentId incidentId) {
        return mutateExisting(incidentId, (actor, incident) -> {
            if (!authorizationPolicy.authorizeClaim(incident).isAllowed()) {
                return unavailable();
            }
            Incident claimed = lifecycle.claim(incident, actor.id());
            return commit(
                    actor,
                    IncidentMutation.update(claimed),
                    IncidentAction.CLAIM,
                    AuditAction.INCIDENT_CLAIMED,
                    IncidentAuditChanges.assignmentChanges(incident, claimed),
                    Optional.empty());
        });
    }

    public ApplicationResult<IncidentView> resolve(IncidentId incidentId, String remarks) {
        ValidationResult validation = requiredTextValidator.validate(RESOLUTION_REMARKS, remarks);
        if (!validation.isValid()) {
            return validationFailure(validation);
        }
        return mutateExisting(incidentId, (actor, incident) -> {
            if (!authorizationPolicy.authorizeResolve(incident).isAllowed()) {
                return unavailable();
            }
            Incident resolved = lifecycle.resolve(incident, actor.id(), remarks);
            String evidenceId = incident.id().value() + ":resolution:" + resolved.resolutionCycles().size();
            return commit(
                    actor,
                    IncidentMutation.update(resolved),
                    IncidentAction.RESOLVE,
                    AuditAction.INCIDENT_RESOLVED,
                    IncidentAuditChanges.statusChange(incident, resolved),
                    Optional.of(new AuditEvidenceReference(AuditEvidenceType.RESOLUTION, evidenceId)));
        });
    }

    public ApplicationResult<IncidentView> handoff(IncidentId incidentId) {
        return transition(incidentId, authorizationPolicy::authorizeHandoff, lifecycle::handoff,
                IncidentAction.HANDOFF, AuditAction.INCIDENT_HANDED_OFF);
    }

    public ApplicationResult<IncidentView> reassign(IncidentId incidentId, AccountId responderId) {
        Objects.requireNonNull(responderId, "responderId");
        return mutateExisting(incidentId, (actor, incident) -> {
            Optional<Account> responder = findAccount(responderId);
            if (responder.isEmpty()
                    || !authorizationPolicy.authorizeReassign(incident, responder.orElseThrow()).isAllowed()) {
                return unavailable();
            }
            Incident reassigned = lifecycle.reassign(incident, responderId);
            return commit(actor, IncidentMutation.update(reassigned), IncidentAction.REASSIGN,
                    AuditAction.INCIDENT_REASSIGNED, IncidentAuditChanges.assignmentChanges(incident, reassigned), Optional.empty());
        });
    }

    public ApplicationResult<IncidentView> reopen(IncidentId incidentId, String explanation) {
        ValidationResult validation = requiredTextValidator.validate(REOPEN_EXPLANATION, explanation);
        if (!validation.isValid()) {
            return validationFailure(validation);
        }
        return mutateExisting(incidentId, (actor, incident) -> {
            if (!authorizationPolicy.authorizeReopen(incident, explanation).isAllowed()) {
                return unavailable();
            }
            ReopenTransition transition = lifecycle.reopen(incident, actor.id(), explanation);
            IncidentComment comment = new IncidentComment(
                    generatedCommentId(), incident.id(), actor.id(), actor.role(), transition.explanation().createdAt(),
                    CommentType.REOPEN_EXPLANATION, transition.explanation().text());
            String evidenceId = incident.id().value() + ":reopen:" + transition.explanation().resolutionCycleNumber();
            return commit(actor, IncidentMutation.reopen(transition.incident(), transition.explanation(), comment),
                    IncidentAction.REOPEN, AuditAction.INCIDENT_REOPENED,
                    IncidentAuditChanges.statusChange(incident, transition.incident()),
                    Optional.of(new AuditEvidenceReference(AuditEvidenceType.COMMENT, evidenceId)));
        });
    }

    public ApplicationResult<IncidentView> detail(IncidentId incidentId) {
        try {
        Optional<Incident> incident = incidentStore.findById(
                    Objects.requireNonNull(incidentId, "incidentId"));
            if (incident.isEmpty()
                    || !authorizationPolicy.authorizeViewIncident(incident.orElseThrow()).isAllowed()) {
                return unavailable();
            }
            return ApplicationResult.success(IncidentView.from(incident.orElseThrow()));
        } catch (RepositoryException exception) {
            return storageFailure(exception);
        }
    }

    public ApplicationResult<List<IncidentView>> list(IncidentSort sort) {
        Objects.requireNonNull(sort, "sort");
        Optional<Account> actor = currentActor();
        if (actor.isEmpty()) {
            return unavailable();
        }
        try {
            List<Incident> incidents = queryFor(actor.orElseThrow(), sort);
            List<IncidentView> authorized = incidents.stream()
                    .filter(incident -> authorizationPolicy.authorizeListEntry(incident).isAllowed())
                    .sorted(sort.comparator())
                    .map(IncidentView::from)
                    .toList();
            return ApplicationResult.success(authorized);
        } catch (RepositoryException exception) {
            return storageFailure(exception);
        }
    }

    /** Searches the current actor's authorized incident scope using shared criteria. */
    public ApplicationResult<List<IncidentView>> search(IncidentSearchCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        Optional<Account> actor = currentActor();
        if (actor.isEmpty()) {
            return unavailable();
        }
        try {
            Account current = actor.orElseThrow();
            IncidentSearchCriteria authorizedCriteria = current.role() == Role.REPORTER
                    ? criteria.withoutIdentityFilters()
                    : criteria;
            List<Incident> incidents = queryFor(current, authorizedCriteria);
            return ApplicationResult.success(incidents.stream()
                    .filter(incident -> authorizationPolicy.authorizeListEntry(incident).isAllowed())
                    .sorted(authorizedCriteria.sort().comparator())
                    .map(IncidentView::from)
                    .toList());
        } catch (RepositoryException exception) {
            return storageFailure(exception);
        }
    }

    /** Reads both responder queues through current authorization and privacy-safe mapping. */
    public ApplicationResult<ResponderDashboardModel> responderDashboard(IncidentPresentationMapper mapper) {
        Objects.requireNonNull(mapper, "mapper");
        try {
            Optional<Account> actor = currentActor();
            if (actor.isEmpty() || actor.orElseThrow().role() != Role.RESPONDER) {
                return unavailable();
            }
            IncidentSort order = IncidentSort.queueOrder();
            List<Incident> authorized = queryFor(actor.orElseThrow(), order).stream()
                    .filter(incident -> authorizationPolicy.authorizeListEntry(incident).isAllowed())
                    .sorted(order.comparator())
                    .toList();
            List<IncidentRowModel> eligible = dashboardRows(authorized, IncidentStatus.SUBMITTED, mapper);
            List<IncidentRowModel> assigned = dashboardRows(authorized, IncidentStatus.ASSIGNED, mapper);
            if (!actor.equals(currentActor())) {
                return unavailable();
            }
            return ApplicationResult.success(new ResponderDashboardModel(eligible, assigned));
        } catch (SecurityException exception) {
            // A permission change during mapping invalidates the entire snapshot.
            return unavailable();
        } catch (RepositoryException exception) {
            return storageFailure(exception);
        }
    }

    /** Reads the administrator incident view with authorization rechecked around mapping. */
    public ApplicationResult<List<IncidentRowModel>> administratorIncidents(
            IncidentSearchCriteria criteria, IncidentPresentationMapper mapper) {
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(mapper, "mapper");
        try {
            Optional<Account> actor = currentActor();
            if (actor.isEmpty() || actor.orElseThrow().role() != Role.ADMINISTRATOR) {
                return unavailable();
            }
            Account administrator = actor.orElseThrow();
            List<IncidentRowModel> rows = queryFor(administrator, criteria).stream()
                    .filter(incident -> authorizationPolicy.authorizeListEntry(incident).isAllowed())
                    .sorted(criteria.sort().comparator())
                    .map(mapper::toRow)
                    .toList();
            if (!actor.equals(currentActor())) {
                return unavailable();
            }
            return ApplicationResult.success(rows);
        } catch (SecurityException exception) {
            return unavailable();
        } catch (RepositoryException exception) {
            return storageFailure(exception);
        }
    }

    private List<IncidentRowModel> dashboardRows(
            List<Incident> incidents, IncidentStatus status, IncidentPresentationMapper mapper) {
        return incidents.stream().filter(incident -> incident.status() == status).map(mapper::toRow).toList();
    }

    private List<Incident> queryFor(Account actor, IncidentSearchCriteria criteria) {
        return switch (actor.role()) {
        case REPORTER -> incidentStore.find(IncidentQuery.reporterOwned(actor.id()), criteria);
        case ADMINISTRATOR -> incidentStore.find(IncidentQuery.administratorAll(), criteria);
        case RESPONDER -> {
            List<Incident> combined = new ArrayList<>(incidentStore.find(
                    IncidentQuery.responderEligibleUnassigned(actor.responderAccess().categories()), criteria));
            combined.addAll(incidentStore.find(IncidentQuery.responderAssigned(actor.id()), criteria));
            yield combined;
        }
        };
    }

    private List<Incident> queryFor(Account actor, IncidentSort sort) {
        return switch (actor.role()) {
        case REPORTER -> incidentStore.find(IncidentQuery.reporterOwned(actor.id()), sort);
        case ADMINISTRATOR -> incidentStore.find(IncidentQuery.administratorAll(), sort);
        case RESPONDER -> {
            List<Incident> combined = new ArrayList<>(incidentStore.find(
                    IncidentQuery.responderEligibleUnassigned(actor.responderAccess().categories()), sort));
            combined.addAll(incidentStore.find(IncidentQuery.responderAssigned(actor.id()), sort));
            yield combined;
        }
        };
    }

    private ApplicationResult<IncidentView> transition(
            IncidentId incidentId,
            Function<Incident, AuthorizationDecision> authorize,
            Function<Incident, Incident> operation,
            IncidentAction incidentAction,
            AuditAction auditAction) {
        return mutateExisting(incidentId, (actor, incident) -> {
            if (!authorize.apply(incident).isAllowed()) {
                return unavailable();
            }
            Incident changed = operation.apply(incident);
            return commit(actor, IncidentMutation.update(changed), incidentAction, auditAction,
                    IncidentAuditChanges.statusAndAssignmentChanges(incident, changed), Optional.empty());
        });
    }

    private ApplicationResult<IncidentView> mutateExisting(IncidentId incidentId, ExistingMutation operation) {
        Objects.requireNonNull(incidentId, "incidentId");
        Optional<Account> actor = currentActor();
        if (actor.isEmpty()) {
            return unavailable();
        }
        try {
            Optional<Incident> incident = incidentStore.findById(incidentId);
            if (incident.isEmpty()) {
                return unavailable();
            }
            return operation.apply(actor.orElseThrow(), incident.orElseThrow());
        } catch (InvalidIncidentTransitionException exception) {
            return failure(ApplicationErrorCode.INVALID_STATE);
        } catch (RepositoryException exception) {
            return storageFailure(exception);
        }
    }

    private ApplicationResult<IncidentView> commit(
            Account actor,
            IncidentMutation mutation,
            IncidentAction incidentAction,
            AuditAction auditAction,
            List<AuditChange> changes,
            Optional<AuditEvidenceReference> evidence) {
        AuditEvent auditEvent = auditEventFactory.create(
                actor,
                visibility(actor, mutation.incident()),
                auditAction,
                new AuditTarget(AuditTargetType.INCIDENT, mutation.incident().id().value().toString()),
                AuditOutcome.SUCCESS,
                changes,
                evidence);
        try {
            incidentStore.commit(new AuditedMutation<>(mutation, auditEvent));
        } catch (RepositoryException exception) {
            return storageFailure(exception);
        }
        eventPublisher.publish(new IncidentChangedEvent(
                mutation.incident().id(), incidentAction, actor.id()));
        return ApplicationResult.success(IncidentView.from(mutation.incident()));
    }

    private Optional<Account> currentActor() {
        return sessionProvider.currentAccount().filter(Account::isEnabled);
    }

    private Optional<Account> findAccount(AccountId accountId) {
        return accountRepository.findById(accountId).filter(Account::isEnabled);
    }

    private IncidentId generatedIncidentId() {
        return Objects.requireNonNull(incidentIdentifierGenerator.get(), "generated incident identifier");
    }

    private CommentId generatedCommentId() {
        return Objects.requireNonNull(commentIdentifierGenerator.get(), "generated comment identifier");
    }

    private ValidationResult validateSubmittedContent(
            String title,
            String description,
            IncidentCategory category) {
        return requiredTextValidator.validate(TITLE, title)
                .combine(requiredTextValidator.validate(DESCRIPTION, description))
                .combine(validateCategory(category));
    }

    private static ValidationResult validateCategory(IncidentCategory category) {
        if (category != null) {
            return ValidationResult.valid();
        }
        return ValidationResult.invalid(new ValidationError(CATEGORY, ValidationErrorCode.REQUIRED));
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static AuditActorVisibility visibility(Account actor, Incident incident) {
        boolean anonymousReporter = incident.anonymous()
                && actor.role() == Role.REPORTER
                && actor.id().equals(incident.reporterId());
        return anonymousReporter ? AuditActorVisibility.ANONYMOUS_REPORTER : AuditActorVisibility.STANDARD;
    }

    private static <T> ApplicationResult<T> validationFailure(ValidationResult validation) {
        return ApplicationResult.failure(ApplicationError.validation(validation));
    }

    private static <T> ApplicationResult<T> unavailable() {
        return failure(ApplicationErrorCode.RESOURCE_UNAVAILABLE);
    }

    private static <T> ApplicationResult<T> failure(ApplicationErrorCode code) {
        return ApplicationResult.failure(ApplicationError.of(code));
    }

    private static <T> ApplicationResult<T> storageFailure(RepositoryException exception) {
        ApplicationErrorCode code = exception.code() == StorageFailureCode.CORRUPT_DATA
                ? ApplicationErrorCode.CORRUPT_DATA
                : ApplicationErrorCode.PERSISTENCE_FAILURE;
        return failure(code);
    }

    @FunctionalInterface
    private interface ExistingMutation {
        ApplicationResult<IncidentView> apply(Account actor, Incident incident);
    }
}

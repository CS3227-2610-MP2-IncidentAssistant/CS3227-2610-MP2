package com.company.incidentdesk.application.slo;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.application.validation.ValidationError;
import com.company.incidentdesk.application.validation.ValidationErrorCode;
import com.company.incidentdesk.application.validation.ValidationField;
import com.company.incidentdesk.application.validation.ValidationResult;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditChange;
import com.company.incidentdesk.domain.audit.AuditChangeField;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.slo.SloConfigurationHistory;
import com.company.incidentdesk.domain.slo.SloTarget;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.domain.slo.SloTargetVersionId;
import com.company.incidentdesk.persistence.AuditedMutation;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.SloConfigurationStore;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** Administers per-category SLO targets as versioned, audited configuration changes. */
public final class SloConfigurationService {
    private static final ValidationField TIME_TO_CLAIM_TARGET = new ValidationField("slo.timeToClaimTarget");
    private static final ValidationField TIME_IN_PROGRESS_TARGET = new ValidationField("slo.timeInProgressTarget");
    private static final ValidationField REOPEN_RATE_TARGET = new ValidationField("slo.reopenRateTarget");

    private final SessionProvider sessionProvider;
    private final AccountAuthorizationPolicy authorizationPolicy;
    private final SloConfigurationStore store;
    private final AuditEventFactory auditEventFactory;
    private final Clock clock;
    private final Supplier<SloTargetVersionId> identifierGenerator;

    public SloConfigurationService(
            SessionProvider sessionProvider,
            AccountAuthorizationPolicy authorizationPolicy,
            SloConfigurationStore store,
            AuditEventFactory auditEventFactory,
            Clock clock,
            Supplier<SloTargetVersionId> identifierGenerator) {
        this.sessionProvider = Objects.requireNonNull(sessionProvider, "sessionProvider");
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy, "authorizationPolicy");
        this.store = Objects.requireNonNull(store, "store");
        this.auditEventFactory = Objects.requireNonNull(auditEventFactory, "auditEventFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identifierGenerator = Objects.requireNonNull(identifierGenerator, "identifierGenerator");
    }

    /** Appends a new, immediately effective SLO target version for a category. */
    public ApplicationResult<SloTargetVersion> configure(
            IncidentCategory category,
            Duration timeToClaimTarget,
            Duration timeInProgressTarget,
            double reopenRateTarget) {
        Objects.requireNonNull(category, "category");
        Optional<Account> actor = currentAdministrator();
        if (actor.isEmpty()) {
            return unavailable();
        }
        ValidationResult validation = validate(timeToClaimTarget, timeInProgressTarget, reopenRateTarget);
        if (!validation.isValid()) {
            return ApplicationResult.failure(ApplicationError.validation(validation));
        }

        SloTarget target = new SloTarget(timeToClaimTarget, timeInProgressTarget, reopenRateTarget);
        Optional<SloTargetVersion> previous = latest(category);
        SloTargetVersion version = new SloTargetVersion(
                Objects.requireNonNull(identifierGenerator.get(), "generated SLO target version identifier"),
                category,
                target,
                clock.instant(),
                actor.orElseThrow().id());

        AuditEvent auditEvent = auditEventFactory.create(
                actor.orElseThrow(),
                AuditActorVisibility.STANDARD,
                AuditAction.SLO_CONFIGURATION_CHANGED,
                new AuditTarget(AuditTargetType.SLO_CONFIGURATION, category.name()),
                AuditOutcome.SUCCESS,
                changes(previous, version),
                Optional.empty());

        try {
            store.commit(new AuditedMutation<>(version, auditEvent));
        } catch (RepositoryException exception) {
            return storageFailure(exception);
        }
        return ApplicationResult.success(version);
    }

    /** Returns the latest configured version for every category that has one. */
    public ApplicationResult<List<SloTargetVersion>> currentTargets() {
        if (currentAdministrator().isEmpty()) {
            return unavailable();
        }
        SloConfigurationHistory history = new SloConfigurationHistory(store.findAll());
        List<SloTargetVersion> latestByCategory = new ArrayList<>();
        for (IncidentCategory category : IncidentCategory.values()) {
            history.latest(category).ifPresent(latestByCategory::add);
        }
        return ApplicationResult.success(List.copyOf(latestByCategory));
    }

    /** Returns one category's full version history in chronological order. */
    public ApplicationResult<List<SloTargetVersion>> history(IncidentCategory category) {
        Objects.requireNonNull(category, "category");
        if (currentAdministrator().isEmpty()) {
            return unavailable();
        }
        List<SloTargetVersion> ordered = store.findByCategory(category).stream()
                .sorted(Comparator.comparing(SloTargetVersion::effectiveFrom)
                        .thenComparing(version -> version.id().value()))
                .toList();
        return ApplicationResult.success(ordered);
    }

    private Optional<SloTargetVersion> latest(IncidentCategory category) {
        return new SloConfigurationHistory(store.findAll()).latest(category);
    }

    private Optional<Account> currentAdministrator() {
        if (!authorizationPolicy.authorizeSloConfiguration().isAllowed()) {
            return Optional.empty();
        }
        return sessionProvider.currentAccount().filter(Account::isEnabled);
    }

    private static List<AuditChange> changes(Optional<SloTargetVersion> previous, SloTargetVersion next) {
        String afterValue = next.id().value().toString();
        return previous
                .map(before -> List.of(AuditChange.changed(
                        AuditChangeField.SLO_VERSION, before.id().value().toString(), afterValue)))
                .orElseGet(() -> List.of(AuditChange.added(AuditChangeField.SLO_VERSION, afterValue)));
    }

    private ValidationResult validate(
            Duration timeToClaimTarget,
            Duration timeInProgressTarget,
            double reopenRateTarget) {
        ValidationResult result = ValidationResult.valid();
        if (timeToClaimTarget == null || timeToClaimTarget.isNegative()) {
            result = result.combine(ValidationResult.invalid(
                    new ValidationError(TIME_TO_CLAIM_TARGET, ValidationErrorCode.OUT_OF_RANGE)));
        }
        if (timeInProgressTarget == null || timeInProgressTarget.isNegative()) {
            result = result.combine(ValidationResult.invalid(
                    new ValidationError(TIME_IN_PROGRESS_TARGET, ValidationErrorCode.OUT_OF_RANGE)));
        }
        if (!Double.isFinite(reopenRateTarget) || reopenRateTarget < 0 || reopenRateTarget > 1) {
            result = result.combine(ValidationResult.invalid(
                    new ValidationError(REOPEN_RATE_TARGET, ValidationErrorCode.OUT_OF_RANGE)));
        }
        return result;
    }

    private static <T> ApplicationResult<T> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }

    private static <T> ApplicationResult<T> storageFailure(RepositoryException exception) {
        ApplicationErrorCode code = exception.code() == StorageFailureCode.CORRUPT_DATA
                ? ApplicationErrorCode.CORRUPT_DATA
                : ApplicationErrorCode.PERSISTENCE_FAILURE;
        return ApplicationResult.failure(ApplicationError.of(code));
    }
}

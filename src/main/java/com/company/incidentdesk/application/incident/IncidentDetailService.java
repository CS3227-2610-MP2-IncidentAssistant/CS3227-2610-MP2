package com.company.incidentdesk.application.incident;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.comment.IncidentCommentService;
import com.company.incidentdesk.application.presentation.AttachmentModel;
import com.company.incidentdesk.application.presentation.CommentModel;
import com.company.incidentdesk.application.presentation.IncidentDetailModel;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.presentation.SloSummaryModel;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.slo.SloCalculator;
import com.company.incidentdesk.domain.slo.SloComplianceState;
import com.company.incidentdesk.domain.slo.SloConfigurationHistory;
import com.company.incidentdesk.domain.slo.SloLiveMetricType;
import com.company.incidentdesk.domain.slo.SloStatusModel;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.persistence.IncidentRepository;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.SloConfigurationStore;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** Authorized application-level composition of data required by an incident detail view. */
public final class IncidentDetailService {
    private final SessionProvider sessions;
    private final IncidentRepository incidents;
    private final IncidentAuthorizationPolicy authorization;
    private final IncidentPresentationMapper mapper;
    private final IncidentCommentService comments;
    private final AttachmentService attachments;
    private final SloConfigurationStore sloConfigurations;
    private final Clock clock;

    public IncidentDetailService(SessionProvider sessions, IncidentRepository incidents,
            IncidentAuthorizationPolicy authorization, IncidentPresentationMapper mapper,
            IncidentCommentService comments, AttachmentService attachments,
            SloConfigurationStore sloConfigurations, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.comments = Objects.requireNonNull(comments, "comments");
        this.attachments = Objects.requireNonNull(attachments, "attachments");
        this.sloConfigurations = Objects.requireNonNull(sloConfigurations, "sloConfigurations");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Loads one privacy-safe detail snapshot, rechecking the active account after related reads. */
    public ApplicationResult<IncidentDetailModel> detail(IncidentId id) {
        Objects.requireNonNull(id, "id");
        BooleanSupplier authorized = viewGuard(id);
        if (!authorized.getAsBoolean()) {
            return unavailable();
        }
        try {
            Optional<Incident> found = incidents.findById(id);
            if (found.isEmpty() || !authorization.authorizeViewIncident(found.orElseThrow()).isAllowed()) {
                return unavailable();
            }
            Incident incident = found.orElseThrow();
            ApplicationResult<List<CommentModel>> commentResult = comments.list(id);
            ApplicationResult<List<AttachmentModel>> attachmentResult = attachments.list(id);
            if (!commentResult.isSuccess() || !attachmentResult.isSuccess()) {
                return unavailable();
            }
            SloSummaryModel slo = sloSummary(incident);
            IncidentDetailModel detail = mapper.toDetail(incident,
                    commentResult.value().orElseThrow(), attachmentResult.value().orElseThrow(), slo);
            if (!authorized.getAsBoolean()) {
                return unavailable();
            }
            return ApplicationResult.success(detail);
        } catch (RepositoryException exception) {
            ApplicationErrorCode code = exception.code() == StorageFailureCode.CORRUPT_DATA
                    ? ApplicationErrorCode.CORRUPT_DATA : ApplicationErrorCode.PERSISTENCE_FAILURE;
            return ApplicationResult.failure(ApplicationError.of(code));
        } catch (SecurityException exception) {
            return unavailable();
        }
    }

    /** Pins the session and permissions for a view while checking current incident access. */
    public BooleanSupplier viewGuard(IncidentId id) {
        Objects.requireNonNull(id, "id");
        Optional<ViewContext> context = currentContext();
        return () -> {
            try {
                return context.isPresent() && context.equals(currentContext())
                        && incidents.findById(id)
                                .filter(incident -> authorization.authorizeViewIncident(incident).isAllowed())
                                .isPresent();
            } catch (RepositoryException exception) {
                return false;
            }
        };
    }

    private Optional<ViewContext> currentContext() {
        try {
            Optional<AuthenticatedSession> session = sessions.currentSession();
            Optional<Account> account = sessions.currentAccount().filter(Account::isEnabled);
            if (session.isEmpty() || account.isEmpty()
                    || !session.orElseThrow().accountId().equals(account.orElseThrow().id())) {
                return Optional.empty();
            }
            return Optional.of(new ViewContext(session.orElseThrow(), account.orElseThrow()));
        } catch (RepositoryException exception) {
            return Optional.empty();
        }
    }

    private record ViewContext(AuthenticatedSession session, Account account) { }

    private SloSummaryModel sloSummary(Incident incident) {
        var now = clock.instant();
        Optional<SloTargetVersion> target = new SloConfigurationHistory(sloConfigurations.findAll())
                .targetFor(incident.category(), now);
        SloStatusModel status = SloCalculator.liveStatus(incident, target, now);
        if (status.state() == SloComplianceState.NOT_APPLICABLE) {
            return new SloSummaryModel(target.isPresent() ? "Not applicable" : "Not configured", 0, false);
        }
        Duration elapsed = status.elapsed().orElseThrow();
        Duration limit = status.target().orElseThrow();
        String metric = status.metric().orElseThrow() == SloLiveMetricType.TIME_TO_CLAIM
                ? "Time to claim" : "Time in progress";
        boolean overdue = status.state() == SloComplianceState.OVERDUE;
        String label = (overdue ? "Overdue" : "Within target") + " · " + metric
                + " · " + elapsed.toMinutes() + "m / " + limit.toMinutes() + "m";
        return new SloSummaryModel(label, progress(elapsed, limit), overdue);
    }

    private static double progress(Duration elapsed, Duration target) {
        if (target.isZero()) {
            return elapsed.isZero() ? 0 : 1;
        }
        double elapsedSeconds = elapsed.getSeconds() + elapsed.getNano() / 1_000_000_000.0;
        double targetSeconds = target.getSeconds() + target.getNano() / 1_000_000_000.0;
        return Math.max(0, Math.min(1, elapsedSeconds / targetSeconds));
    }

    private static <T> ApplicationResult<T> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }
}

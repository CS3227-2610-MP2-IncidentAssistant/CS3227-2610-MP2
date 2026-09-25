package com.company.incidentdesk.application.incident;

import java.time.Clock;
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
import com.company.incidentdesk.application.slo.SloIncidentClassifier;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentId;
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
    private final SloIncidentClassifier sloSummaries;

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
        sloSummaries = new SloIncidentClassifier(sloConfigurations, clock);
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
            SloSummaryModel slo = sloSummaries.summarize(incident);
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
        BooleanSupplier sessionMatches = sessionGuard();
        return () -> {
            try {
                return sessionMatches.getAsBoolean()
                        && incidents.findById(id)
                                .filter(incident -> authorization.authorizeViewIncident(incident).isAllowed())
                                .isPresent();
            } catch (RepositoryException exception) {
                return false;
            }
        };
    }

    /** Keeps completion navigation bound to its actor even when a mutation removes detail access. */
    public BooleanSupplier sessionGuard() {
        Optional<ViewContext> context = currentContext();
        return () -> context.isPresent() && context.equals(currentContext());
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

    private static <T> ApplicationResult<T> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }
}

package com.company.incidentdesk.application.attachment;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.presentation.AttachmentModel;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.application.validation.ValidationError;
import com.company.incidentdesk.application.validation.ValidationErrorCode;
import com.company.incidentdesk.application.validation.ValidationField;
import com.company.incidentdesk.application.validation.ValidationResult;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.attachment.AttachmentId;
import com.company.incidentdesk.domain.attachment.AttachmentType;
import com.company.incidentdesk.domain.attachment.IncidentAttachment;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEvidenceReference;
import com.company.incidentdesk.domain.audit.AuditEvidenceType;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.persistence.AttachmentStore;
import com.company.incidentdesk.persistence.IncidentRepository;
import com.company.incidentdesk.persistence.RepositoryException;

/** All UI attachment reads and additions pass through current incident authorization. */
public final class AttachmentService {
    private static final long BYTES_PER_MIB = 1024L * 1024L;
    private final SessionProvider sessions;
    private final IncidentRepository incidents;
    private final AttachmentStore attachments;
    private final IncidentAuthorizationPolicy authorization;
    private final AttachmentLimits limits;
    private final AttachmentValidator validator;
    private final AuditEventFactory audits;
    private final Clock clock;
    private final Supplier<AttachmentId> identifiers;

    public AttachmentService(SessionProvider sessions, IncidentRepository incidents, AttachmentStore attachments,
            IncidentAuthorizationPolicy authorization, AttachmentLimits limits, AuditEventFactory audits,
            Clock clock, Supplier<AttachmentId> identifiers) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.attachments = Objects.requireNonNull(attachments, "attachments");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.validator = new AttachmentValidator(limits);
        this.audits = Objects.requireNonNull(audits, "audits");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
    }

    /** Presentation-safe guidance derived from the same limits used for validation. */
    public String uploadLimitSummary() {
        String files = limits.count() == 1 ? "file" : "files";
        return "PNG/JPEG " + formatBytes(limits.imageBytes()) + ", MP4 " + formatBytes(limits.videoBytes())
                + "; " + limits.count() + " " + files + " and " + formatBytes(limits.totalBytes())
                + " per incident; images up to " + limits.imagePixels() + " pixels.";
    }

    private static String formatBytes(long bytes) {
        if (bytes % BYTES_PER_MIB == 0) {
            return (bytes / BYTES_PER_MIB) + " MiB";
        }
        // Preserve exact configured bounds rather than rounding up a permitted size.
        return bytes + (bytes == 1 ? " byte" : " bytes");
    }

    public ApplicationResult<List<AttachmentModel>> list(IncidentId incidentId) {
        try {
            Context context = requireContext(incidentId);
            List<AttachmentModel> rows = models(context.incident());
            requireCurrent(context);
            return ApplicationResult.success(rows);
        } catch (SecurityException exception) {
            return unavailable();
        } catch (RepositoryException exception) {
            return failure(ApplicationErrorCode.PERSISTENCE_FAILURE);
        }
    }

    public ApplicationResult<AttachmentModel> add(IncidentId incidentId, Path source) {
        try {
            Context context = requireContext(incidentId);
            if (!authorization.authorizeAttachmentAddition(context.incident()).isAllowed()) {
                return unavailable();
            }
            byte[] bytes = validator.readSource(Objects.requireNonNull(source, "source"));
            AttachmentType type = validator.validate(bytes, source.getFileName().toString());
            IncidentAttachment attachment = new IncidentAttachment(identifiers.get(), incidentId, type, bytes.length,
                    AttachmentValidator.sanitizeName(source.getFileName().toString()), clock.instant());
            requireCurrent(context);
            if (!authorization.authorizeAttachmentAddition(context.incident()).isAllowed()) {
                return unavailable();
            }
            attachments.add(attachment, bytes, context.incident(), context.account(), limits,
                    additionAudit(context, attachment), migrationAudit(context));
            // Avoid another fallible repository read after the operation has committed.
            return ApplicationResult.success(model(attachment, context.incident().anonymous()));
        } catch (SecurityException exception) {
            return unavailable();
        } catch (AttachmentValidationException exception) {
            return invalidFile();
        } catch (IOException | RepositoryException exception) {
            return failure(ApplicationErrorCode.PERSISTENCE_FAILURE);
        }
    }

    public ApplicationResult<AttachmentRead> open(AttachmentId id) {
        try {
            IncidentAttachment attachment = attachments.find(id).orElseThrow(AttachmentService::denied);
            Context context = requireContext(attachment.incidentId());
            if (attachment.sizeBytes() > limits.maximumFileBytes()) {
                return unavailable();
            }
            byte[] bytes = attachments.read(attachment);
            String source = attachments.mediaSource(attachment);
            requireCurrent(context);
            AttachmentRead read = new AttachmentRead(new AttachmentContent(attachment.type(), bytes), source,
                    () -> isCurrent(context));
            return ApplicationResult.success(read);
        } catch (SecurityException exception) {
            return unavailable();
        } catch (RepositoryException exception) {
            return failure(ApplicationErrorCode.PERSISTENCE_FAILURE);
        }
    }

    /** Captures identity, permissions and incident state without exposing them to controls. */
    public BooleanSupplier viewGuard(IncidentId id) {
        try {
            Context context = requireContext(id);
            return () -> isCurrent(context);
        } catch (SecurityException | RepositoryException exception) {
            return () -> false;
        }
    }

    public boolean canAdd(IncidentId id) {
        try {
            Context context = requireContext(id);
            return authorization.authorizeAttachmentAddition(context.incident()).isAllowed();
        } catch (SecurityException | RepositoryException exception) {
            return false;
        }
    }

    private Context requireContext(IncidentId id) {
        AuthenticatedSession session = sessions.currentSession().orElseThrow(AttachmentService::denied);
        Account account = sessions.currentAccount().filter(Account::isEnabled).orElseThrow(AttachmentService::denied);
        Incident incident = incidents.findById(id).orElseThrow(AttachmentService::denied);
        if (!session.accountId().equals(account.id()) || !authorization.authorizeAttachmentAccess(incident).isAllowed()) {
            throw denied();
        }
        return new Context(session, account, incident);
    }

    private boolean isCurrent(Context context) {
        try {
            return context.equals(requireContext(context.incident().id()));
        } catch (SecurityException | RepositoryException exception) {
            return false;
        }
    }

    private void requireCurrent(Context context) {
        if (!isCurrent(context)) {
            throw denied();
        }
    }

    private List<AttachmentModel> models(Incident incident) {
        List<AttachmentModel> result = new ArrayList<>();
        for (IncidentAttachment attachment : attachments.list(incident.id())) {
            result.add(model(attachment, incident.anonymous()));
        }
        return List.copyOf(result);
    }

    private AttachmentModel model(IncidentAttachment attachment, boolean anonymous) {
        String safeName = anonymous
                ? (attachment.type() == AttachmentType.MP4 ? "Video" : "Image") + "." + attachment.type().extension()
                : attachment.displayName();
        return new AttachmentModel(attachment.id().value().toString(), safeName,
                attachment.type().mediaType(), attachment.sizeBytes());
    }

    private AuditEvent additionAudit(Context context, IncidentAttachment attachment) {
        return audits.create(context.account(), visibility(context), AuditAction.ATTACHMENT_ADDED,
                new AuditTarget(AuditTargetType.INCIDENT, context.incident().id().value().toString()),
                AuditOutcome.SUCCESS, List.of(), Optional.of(new AuditEvidenceReference(
                        AuditEvidenceType.ATTACHMENT, attachment.id().value().toString())));
    }

    private AuditEvent migrationAudit(Context context) {
        return audits.create(context.account(), visibility(context), AuditAction.DATA_MIGRATED,
                new AuditTarget(AuditTargetType.APPLICATION_DATA, "schema-1-to-2"),
                AuditOutcome.SUCCESS, List.of(), Optional.empty());
    }

    private AuditActorVisibility visibility(Context context) {
        return context.incident().anonymous() ? AuditActorVisibility.ANONYMOUS_REPORTER : AuditActorVisibility.STANDARD;
    }

    private static SecurityException denied() { return new SecurityException("Attachment unavailable"); }
    private static <T> ApplicationResult<T> unavailable() { return failure(ApplicationErrorCode.RESOURCE_UNAVAILABLE); }
    private static <T> ApplicationResult<T> failure(ApplicationErrorCode code) {
        return ApplicationResult.failure(ApplicationError.of(code));
    }
    private static <T> ApplicationResult<T> invalidFile() {
        return ApplicationResult.failure(ApplicationError.validation(ValidationResult.invalid(
                new ValidationError(new ValidationField("attachment.file"), ValidationErrorCode.OUT_OF_RANGE))));
    }

    private record Context(AuthenticatedSession session, Account account, Incident incident) { }
}

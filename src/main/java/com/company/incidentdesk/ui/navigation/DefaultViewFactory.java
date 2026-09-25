package com.company.incidentdesk.ui.navigation;

import java.util.Objects;
import java.util.function.Consumer;

import com.company.incidentdesk.application.account.AccountDirectoryService;
import com.company.incidentdesk.application.account.AccountDeletionService;
import com.company.incidentdesk.application.account.AccountPasswordResetService;
import com.company.incidentdesk.application.account.PromotionRequestService;
import com.company.incidentdesk.application.account.ResponderAccessService;
import com.company.incidentdesk.application.audit.AuditLogService;
import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.comment.IncidentCommentService;
import com.company.incidentdesk.application.incident.IncidentDetailService;
import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.application.slo.SloConfigurationService;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.ui.admin.AdminIncidentDetailPage;
import com.company.incidentdesk.ui.admin.AdminIncidentPage;
import com.company.incidentdesk.ui.admin.AdminAccountsPage;
import com.company.incidentdesk.ui.admin.AdminAuditLogPage;
import com.company.incidentdesk.ui.admin.AdminSloPage;
import com.company.incidentdesk.ui.reporter.ReporterPage;
import com.company.incidentdesk.ui.responder.ResponderPage;
import com.company.incidentdesk.ui.responder.ResponderIncidentPage;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.ui.shared.components.IncidentDetailActions;
import com.company.incidentdesk.ui.shared.components.IncidentDetailView;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.scene.Node;

/** Default role-aware view factory backed by the shared application services. */
public final class DefaultViewFactory implements ViewFactory {
    private final IncidentService incidents;
    private final IncidentPresentationMapper mapper;
    private final SessionProvider sessions;
    private final AccountDirectoryService accounts;
    private final AccountDeletionService accountDeletion;
    private final AccountPasswordResetService passwordResets;
    private final ResponderAccessService responderAccess;
    private final PromotionRequestService promotionRequests;
    private final AuditLogService auditLog;
    private final SloConfigurationService sloConfigurations;
    private final IncidentDetailService incidentDetails;
    private final IncidentCommentService comments;
    private final AttachmentService attachments;

    public DefaultViewFactory(
            IncidentService incidents,
            IncidentPresentationMapper mapper,
            SessionProvider sessions,
            AccountDirectoryService accounts,
            AccountDeletionService accountDeletion,
            AccountPasswordResetService passwordResets,
            ResponderAccessService responderAccess,
            PromotionRequestService promotionRequests,
            AuditLogService auditLog,
            SloConfigurationService sloConfigurations,
            IncidentDetailService incidentDetails,
            IncidentCommentService comments,
            AttachmentService attachments) {
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.accountDeletion = Objects.requireNonNull(accountDeletion, "accountDeletion");
        this.passwordResets = Objects.requireNonNull(passwordResets, "passwordResets");
        this.responderAccess = Objects.requireNonNull(responderAccess, "responderAccess");
        this.promotionRequests = Objects.requireNonNull(promotionRequests, "promotionRequests");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.sloConfigurations = Objects.requireNonNull(sloConfigurations, "sloConfigurations");
        this.incidentDetails = Objects.requireNonNull(incidentDetails, "incidentDetails");
        this.comments = Objects.requireNonNull(comments, "comments");
        this.attachments = Objects.requireNonNull(attachments, "attachments");
    }

    @Override
    public Node createView(Account account, ApplicationRoute route, Consumer<IncidentId> onOpenIncident) {
        if (!route.isAvailableTo(account.role())) {
            return UiComponents.feedback("Administrator area unavailable",
                    "Sign in with an administrator account to continue.", FeedbackType.EMPTY);
        }
        if (route.isAdministratorOnly()) {
            return switch (route) {
            case ADMIN_ACCOUNTS -> new AdminAccountsPage(
                    accounts, accountDeletion, passwordResets, responderAccess, promotionRequests);
            case ADMIN_SLO -> new AdminSloPage(sloConfigurations);
            case ADMIN_AUDIT_LOG -> new AdminAuditLogPage(auditLog);
            case DASHBOARD -> throw new IllegalStateException("Dashboard handled below");
            };
        }
        return switch (account.role()) {
        case REPORTER -> new ReporterPage(incidents);
        case RESPONDER -> new ResponderPage(incidents, mapper, sessions, onOpenIncident);
        case ADMINISTRATOR -> new AdminIncidentPage(
                incidents, mapper, sessions, sloConfigurations, onOpenIncident);
        };
    }

    @Override
    public Node createIncidentDetail(Account account, IncidentId incidentId, Runnable onBack) {
        Objects.requireNonNull(account, "account");
        if (account.role() == Role.RESPONDER) {
            return new ResponderIncidentPage(incidents, incidentDetails, comments, attachments, incidentId, onBack);
        }
        if (account.role() == Role.ADMINISTRATOR) {
            return new AdminIncidentDetailPage(
                    incidents, incidentDetails, comments, attachments, accounts, incidentId, onBack);
        }
        IncidentDetailView detail = new IncidentDetailView(
                incidentDetails, comments, attachments, incidentId, onBack, IncidentDetailActions.none());
        detail.setAccessibleText("Incident detail for " + incidentId.value());
        return detail;
    }
}

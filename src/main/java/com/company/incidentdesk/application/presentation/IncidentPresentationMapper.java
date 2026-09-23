package com.company.incidentdesk.application.presentation;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.company.incidentdesk.application.account.AccountLookup;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.ResolutionCycle;

/** Maps authorized domain incidents into immutable, privacy-safe presentation models. */
public final class IncidentPresentationMapper {
    public static final String ANONYMOUS_REPORTER_LABEL = "Anonymous reporter";
    public static final String UNASSIGNED_LABEL = "Unassigned";
    public static final String UNAVAILABLE_LABEL = "Unavailable";

    private final AccountLookup accountLookup;
    private final IncidentAuthorizationPolicy authorizationPolicy;
    private final ZoneId displayZone;
    private final DateTimeFormatter formatter;

    /** Creates a mapper with an explicit display timezone and formatter. */
    public IncidentPresentationMapper(
            AccountLookup accountLookup,
            IncidentAuthorizationPolicy authorizationPolicy,
            ZoneId displayZone,
            DateTimeFormatter formatter) {
        this.accountLookup = Objects.requireNonNull(accountLookup, "accountLookup");
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy, "authorizationPolicy");
        this.displayZone = Objects.requireNonNull(displayZone, "displayZone");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    /** Maps an incident to a compact row after current-view authorization succeeds. */
    public IncidentRowModel toRow(Incident incident) {
        requireViewAuthorization(incident);
        return new IncidentRowModel(
                incident.id(),
                incident.title(),
                IncidentDisplayLabels.category(incident.category()),
                IncidentDisplayLabels.status(incident.status()),
                reporterLabel(incident),
                incident.assigneeId().map(this::accountLabel).orElse(UNASSIGNED_LABEL),
                format(incident.createdAt()),
                incident.anonymous(),
                incident.reopenCount(),
                actions(incident));
    }

    /** Maps incident-owned data and already-authorized related display models to a detail view. */
    public IncidentDetailModel toDetail(
            Incident incident,
            List<CommentModel> comments,
            List<AttachmentModel> attachments,
            SloSummaryModel slo) {
        requireViewAuthorization(incident);
        List<CommentModel> safeComments = authorizationPolicy.authorizeViewComments(incident).isAllowed()
                ? List.copyOf(comments) : List.of();
        List<AttachmentModel> safeAttachments = authorizationPolicy.authorizeAttachmentAccess(incident).isAllowed()
                ? List.copyOf(attachments) : List.of();
        return new IncidentDetailModel(
                toRow(incident),
                incident.description(),
                incident.submittedAt().map(this::format).orElse(""),
                incident.withdrawnAt().map(this::format).orElse(""),
                queueSummary(incident),
                resolutions(incident),
                safeComments,
                safeAttachments,
                Objects.requireNonNull(slo, "slo"));
    }

    private IncidentActionModel actions(Incident incident) {
        return new IncidentActionModel(
                authorizationPolicy.authorizeEdit(incident, incident.anonymous()).isAllowed(),
                authorizationPolicy.authorizeWithdraw(incident).isAllowed(),
                authorizationPolicy.authorizeClaim(incident).isAllowed(),
                authorizationPolicy.authorizeResolve(incident).isAllowed(),
                authorizationPolicy.authorizeHandoff(incident).isAllowed(),
                authorizationPolicy.authorizeReassign(incident).isAllowed(),
                authorizationPolicy.authorizeReopen(incident).isAllowed(),
                authorizationPolicy.authorizeComment(incident).isAllowed(),
                authorizationPolicy.authorizeAttachmentAccess(incident).isAllowed());
    }

    private String reporterLabel(Incident incident) {
        if (incident.anonymous()) {
            return ANONYMOUS_REPORTER_LABEL;
        }
        return accountLabel(incident.reporterId());
    }

    private String accountLabel(AccountId accountId) {
        return accountLookup.findById(accountId).map(Account::loginName).orElse(UNAVAILABLE_LABEL);
    }

    private QueueSummaryModel queueSummary(Incident incident) {
        return incident.currentCycle()
                .map(cycle -> new QueueSummaryModel(
                        format(cycle.queueEnteredAt()),
                        cycle.firstAssignedAt().map(this::format).orElse(""),
                        cycle.latestAssignedAt().map(this::format).orElse("")))
                .orElseGet(() -> new QueueSummaryModel("", "", ""));
    }

    private List<ResolutionModel> resolutions(Incident incident) {
        List<ResolutionModel> result = new ArrayList<>();
        for (int index = 0; index < incident.resolutionCycles().size(); index++) {
            ResolutionCycle cycle = incident.resolutionCycles().get(index);
            int cycleNumber = index + 1;
            cycle.resolution().ifPresent(resolution -> result.add(new ResolutionModel(
                    cycleNumber,
                    resolution.remarks(),
                    format(resolution.resolvedAt()),
                    accountLabel(resolution.resolvedBy()))));
        }
        return List.copyOf(result);
    }

    private String format(Instant instant) {
        return formatter.format(instant.atZone(displayZone));
    }

    private void requireViewAuthorization(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        if (!authorizationPolicy.authorizeViewIncident(incident).isAllowed()) {
            throw unauthorized();
        }
    }

    private SecurityException unauthorized() {
        return new SecurityException("Incident is unavailable");
    }
}

package com.company.incidentdesk.application.authorization;

import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentStatus;

/** Authorizes incident and incident-related operations from the current session. */
public final class IncidentAuthorizationPolicy {
    private final SessionProvider sessionProvider;

    /**
     * Creates a policy that reloads the current account for every decision.
     *
     * @param sessionProvider active-session source
     */
    public IncidentAuthorizationPolicy(SessionProvider sessionProvider) {
        this.sessionProvider = Objects.requireNonNull(sessionProvider, "sessionProvider");
    }

    /** Authorizes including an incident in the current actor's list. */
    public AuthorizationDecision authorizeListEntry(Incident incident) {
        return authorizeViewIncident(incident);
    }

    /** Authorizes viewing incident details. */
    public AuthorizationDecision authorizeViewIncident(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        return decide(actor -> canViewIncident(actor, incident));
    }

    /** Authorizes viewing the comments associated with an incident. */
    public AuthorizationDecision authorizeViewComments(Incident incident) {
        return authorizeViewIncident(incident);
    }

    /** Authorizes adding an ordinary comment to an incident. */
    public AuthorizationDecision authorizeComment(Incident incident) {
        return authorizeViewIncident(incident);
    }

    /** Authorizes reading or adding attachments associated with an incident. */
    public AuthorizationDecision authorizeAttachmentAccess(Incident incident) {
        return authorizeViewIncident(incident);
    }

    /** Authorizes creating or saving a report for the supplied owner. */
    public AuthorizationDecision authorizeCreate(AccountId reporterId) {
        Objects.requireNonNull(reporterId, "reporterId");
        return decide(actor -> isReporterOwner(actor, reporterId));
    }

    /** Authorizes submitting a report for the supplied owner. */
    public AuthorizationDecision authorizeSubmit(AccountId reporterId) {
        return authorizeCreate(reporterId);
    }

    /** Authorizes editing mutable report content. */
    public AuthorizationDecision authorizeEdit(Incident incident, boolean requestedAnonymous) {
        Objects.requireNonNull(incident, "incident");
        return decide(actor -> canEdit(actor, incident, requestedAnonymous));
    }

    /** Authorizes withdrawing an unassigned submitted report. */
    public AuthorizationDecision authorizeWithdraw(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        return decide(actor -> isReporterOwner(actor, incident.reporterId())
                && incident.status() == IncidentStatus.SUBMITTED
                && incident.assigneeId().isEmpty());
    }

    /** Authorizes claiming an eligible unassigned report. */
    public AuthorizationDecision authorizeClaim(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        return decide(actor -> isEligibleResponder(actor, incident)
                && incident.status() == IncidentStatus.SUBMITTED
                && incident.assigneeId().isEmpty());
    }

    /** Authorizes resolving an assigned report. */
    public AuthorizationDecision authorizeResolve(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        return decide(actor -> canResolve(actor, incident));
    }

    /** Authorizes handing an assigned report back to its category queue. */
    public AuthorizationDecision authorizeHandoff(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        return decide(actor -> isAssignedEligibleResponder(actor, incident));
    }

    /** Authorizes reassigning an incident to an eligible responder. */
    public AuthorizationDecision authorizeReassign(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        return decide(actor -> canReassign(actor, incident));
    }

    /** Authorizes reassigning an incident to a specific eligible responder. */
    public AuthorizationDecision authorizeReassign(Incident incident, Account targetResponder) {
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(targetResponder, "targetResponder");
        return decide(actor -> canReassign(actor, incident)
                && targetResponder.isEnabled()
                && targetResponder.role() == Role.RESPONDER
                && targetResponder.responderAccess().permits(incident.category()));
    }

    /** Authorizes offering the reopen action for a resolved report. */
    public AuthorizationDecision authorizeReopen(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        return decide(actor -> canReopen(actor, incident));
    }

    /** Authorizes reopening a resolved report with a supplied non-blank explanation. */
    public AuthorizationDecision authorizeReopen(Incident incident, String explanation) {
        Objects.requireNonNull(incident, "incident");
        return decide(actor -> canReopen(actor, incident)
                && explanation != null
                && !explanation.isBlank());
    }

    /** Authorizes revealing the incident reporter's identity. */
    public AuthorizationDecision authorizeViewReporterIdentity(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        return decide(actor -> canViewReporterIdentity(actor, incident));
    }

    private AuthorizationDecision decide(java.util.function.Predicate<Account> rule) {
        Optional<Account> currentAccount = sessionProvider.currentAccount();
        return AuthorizationDecision.from(currentAccount.filter(Account::isEnabled).filter(rule).isPresent());
    }

    /**
     * Evaluates incident visibility for an explicit account without relying on UI-supplied identity.
     *
     * @param actor current account and authorization attributes
     * @param incident incident to evaluate
     * @return true when the account may currently view the incident
     */
    public static boolean canViewIncident(Account actor, Incident incident) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(incident, "incident");
        return switch (actor.role()) {
        case REPORTER -> actor.id().equals(incident.reporterId());
        case RESPONDER -> isEligibleResponder(actor, incident)
                && (isUnassignedSubmitted(incident) || isAssignedTo(actor, incident));
        case ADMINISTRATOR -> true;
        };
    }

    private static boolean canEdit(Account actor, Incident incident, boolean requestedAnonymous) {
        if (!isReporterOwner(actor, incident.reporterId())) {
            return false;
        }
        if (incident.status() == IncidentStatus.DRAFT) {
            return true;
        }
        return incident.status() == IncidentStatus.SUBMITTED
                && incident.assigneeId().isEmpty()
                && (!incident.anonymous() || requestedAnonymous);
    }

    private static boolean canResolve(Account actor, Incident incident) {
        if (incident.status() != IncidentStatus.ASSIGNED) {
            return false;
        }
        return switch (actor.role()) {
        case REPORTER -> false;
        case RESPONDER -> isAssignedEligibleResponder(actor, incident);
        case ADMINISTRATOR -> true;
        };
    }

    private static boolean canReassign(Account actor, Incident incident) {
        return actor.role() == Role.ADMINISTRATOR
                && incident.status() == IncidentStatus.ASSIGNED;
    }

    private static boolean canReopen(Account actor, Incident incident) {
        return isReporterOwner(actor, incident.reporterId())
                && incident.status() == IncidentStatus.RESOLVED;
    }

    private static boolean canViewReporterIdentity(Account actor, Incident incident) {
        if (isReporterOwner(actor, incident.reporterId())) {
            return true;
        }
        return !incident.anonymous()
                && actor.role() != Role.REPORTER
                && canViewIncident(actor, incident);
    }

    private static boolean isReporterOwner(Account actor, AccountId reporterId) {
        return actor.role() == Role.REPORTER && actor.id().equals(reporterId);
    }

    private static boolean isEligibleResponder(Account actor, Incident incident) {
        return actor.role() == Role.RESPONDER
                && actor.responderAccess().permits(incident.category());
    }

    private static boolean isAssignedEligibleResponder(Account actor, Incident incident) {
        return isEligibleResponder(actor, incident)
                && incident.status() == IncidentStatus.ASSIGNED
                && isAssignedTo(actor, incident);
    }

    private static boolean isAssignedTo(Account actor, Incident incident) {
        return incident.assigneeId().filter(actor.id()::equals).isPresent();
    }

    private static boolean isUnassignedSubmitted(Incident incident) {
        return incident.status() == IncidentStatus.SUBMITTED && incident.assigneeId().isEmpty();
    }
}

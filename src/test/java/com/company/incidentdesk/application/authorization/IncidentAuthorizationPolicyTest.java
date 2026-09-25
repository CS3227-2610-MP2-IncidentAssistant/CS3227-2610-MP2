package com.company.incidentdesk.application.authorization;

import static com.company.incidentdesk.application.authorization.AuthorizationDecision.ALLOWED;
import static com.company.incidentdesk.application.authorization.AuthorizationDecision.DENIED;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.OTHER_REPORTER_ID;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.OTHER_RESPONDER_ID;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.REPORTER_ID;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.RESPONDER_ID;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.administrator;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.assigned;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.disabledResponder;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.draft;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.reporter;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.resolved;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.responder;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.submitted;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentCategory;

/** Tests every incident-related row of the authorization matrix. */
class IncidentAuthorizationPolicyTest {
    private AuthorizationTestFixtures.MutableSessionProvider sessionProvider;
    private IncidentAuthorizationPolicy policy;

    @BeforeEach
    void setUp() {
        sessionProvider = new AuthorizationTestFixtures.MutableSessionProvider();
        policy = new IncidentAuthorizationPolicy(sessionProvider);
    }

    @Test
    void reporterCanListAndViewOnlyOwnedIncidents() {
        Incident ownIncident = submitted(false);
        sessionProvider.signIn(reporter(REPORTER_ID));

        assertEquals(ALLOWED, policy.authorizeListEntry(ownIncident));
        assertEquals(ALLOWED, policy.authorizeViewIncident(ownIncident));

        sessionProvider.signIn(reporter(OTHER_REPORTER_ID));

        assertEquals(DENIED, policy.authorizeListEntry(ownIncident));
        assertEquals(DENIED, policy.authorizeViewIncident(ownIncident));
    }

    @Test
    void responderCanViewPermittedQueueAndOwnAssignmentOnly() {
        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.IT));

        assertEquals(ALLOWED, policy.authorizeViewIncident(submitted(false)));
        assertEquals(ALLOWED, policy.authorizeViewIncident(assigned(false)));

        sessionProvider.signIn(responder(OTHER_RESPONDER_ID, IncidentCategory.IT));

        assertEquals(DENIED, policy.authorizeViewIncident(assigned(false)));

        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.FACILITIES));

        assertEquals(DENIED, policy.authorizeViewIncident(submitted(false)));
    }

    @Test
    void responderKeepsViewingAndActingOnAnIncidentAssignedBeforeLosingItsCategory() {
        Incident incident = assigned(false);
        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.FACILITIES));

        assertEquals(ALLOWED, policy.authorizeViewIncident(incident));
        assertEquals(ALLOWED, policy.authorizeResolve(incident));
        assertEquals(ALLOWED, policy.authorizeHandoff(incident));
        assertEquals(DENIED, policy.authorizeClaim(incident));
    }

    @Test
    void administratorCanListAndViewEveryIncident() {
        sessionProvider.signIn(administrator());

        assertEquals(ALLOWED, policy.authorizeListEntry(submitted(false)));
        assertEquals(ALLOWED, policy.authorizeViewIncident(resolved(true)));
    }

    @Test
    void commentsAndAttachmentsUseIncidentDetailAuthorization() {
        Incident incident = submitted(false);
        sessionProvider.signIn(reporter(REPORTER_ID));

        assertEquals(ALLOWED, policy.authorizeViewComments(incident));
        assertEquals(ALLOWED, policy.authorizeComment(incident));
        assertEquals(ALLOWED, policy.authorizeAttachmentAccess(incident));

        sessionProvider.signIn(reporter(OTHER_REPORTER_ID));

        assertEquals(DENIED, policy.authorizeViewComments(incident));
        assertEquals(DENIED, policy.authorizeComment(incident));
        assertEquals(DENIED, policy.authorizeAttachmentAccess(incident));
    }

    @Test
    void onlyOwningReporterCanCreateAndSubmitForSelf() {
        sessionProvider.signIn(reporter(REPORTER_ID));

        assertEquals(ALLOWED, policy.authorizeCreate(REPORTER_ID));
        assertEquals(ALLOWED, policy.authorizeSubmit(REPORTER_ID));
        assertEquals(DENIED, policy.authorizeCreate(OTHER_REPORTER_ID));

        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.IT));

        assertEquals(DENIED, policy.authorizeCreate(RESPONDER_ID));
        assertEquals(DENIED, policy.authorizeSubmit(RESPONDER_ID));
    }

    @Test
    void editAndWithdrawRequireOwningReporterAndMutableState() {
        sessionProvider.signIn(reporter(REPORTER_ID));

        assertEquals(ALLOWED, policy.authorizeEdit(draft(false), true));
        assertEquals(ALLOWED, policy.authorizeEdit(submitted(false), false));
        assertEquals(ALLOWED, policy.authorizeWithdraw(submitted(false)));
        assertEquals(DENIED, policy.authorizeEdit(assigned(false), false));
        assertEquals(DENIED, policy.authorizeWithdraw(assigned(false)));

        sessionProvider.signIn(reporter(OTHER_REPORTER_ID));

        assertEquals(DENIED, policy.authorizeEdit(draft(false), false));
        assertEquals(DENIED, policy.authorizeWithdraw(submitted(false)));
    }

    @Test
    void submittedAnonymousIncidentCannotBeMadeIdentifying() {
        sessionProvider.signIn(reporter(REPORTER_ID));

        assertEquals(DENIED, policy.authorizeEdit(submitted(true), false));
        assertEquals(ALLOWED, policy.authorizeEdit(submitted(true), true));
        assertEquals(ALLOWED, policy.authorizeEdit(draft(true), false));
    }

    @Test
    void claimRequiresResponderCategoryAndUnassignedSubmittedState() {
        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.IT));

        assertEquals(ALLOWED, policy.authorizeClaim(submitted(false)));
        assertEquals(DENIED, policy.authorizeClaim(assigned(false)));

        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.FACILITIES));

        assertEquals(DENIED, policy.authorizeClaim(submitted(false)));

        sessionProvider.signIn(reporter(REPORTER_ID));

        assertEquals(DENIED, policy.authorizeClaim(submitted(false)));
    }

    @Test
    void resolveRequiresOwnAssignmentOrAdministrator() {
        Incident incident = assigned(false);
        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.IT));

        assertEquals(ALLOWED, policy.authorizeResolve(incident));

        sessionProvider.signIn(responder(OTHER_RESPONDER_ID, IncidentCategory.IT));

        assertEquals(DENIED, policy.authorizeResolve(incident));

        sessionProvider.signIn(administrator());

        assertEquals(ALLOWED, policy.authorizeResolve(incident));
        assertEquals(DENIED, policy.authorizeResolve(submitted(false)));
    }

    @Test
    void handoffRequiresAssignedResponder() {
        Incident incident = assigned(false);
        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.IT));

        assertEquals(ALLOWED, policy.authorizeHandoff(incident));

        sessionProvider.signIn(responder(OTHER_RESPONDER_ID, IncidentCategory.IT));

        assertEquals(DENIED, policy.authorizeHandoff(incident));

        sessionProvider.signIn(administrator());

        assertEquals(DENIED, policy.authorizeHandoff(incident));
    }

    @Test
    void reassignRequiresAdministratorAndEligibleTarget() {
        Incident incident = assigned(false);
        Account eligibleTarget = responder(OTHER_RESPONDER_ID, IncidentCategory.IT);
        sessionProvider.signIn(administrator());

        assertEquals(ALLOWED, policy.authorizeReassign(incident));
        assertEquals(ALLOWED, policy.authorizeReassign(incident, eligibleTarget));
        assertEquals(
                DENIED,
                policy.authorizeReassign(
                        incident,
                        responder(OTHER_RESPONDER_ID, IncidentCategory.FACILITIES)));
        assertEquals(
                DENIED,
                policy.authorizeReassign(
                        incident,
                        disabledResponder(OTHER_RESPONDER_ID, IncidentCategory.IT)));

        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.IT));

        assertEquals(DENIED, policy.authorizeReassign(incident));
        assertEquals(DENIED, policy.authorizeReassign(incident, eligibleTarget));
    }

    @Test
    void reopenRequiresOwningReporterResolvedStateAndExplanation() {
        Incident incident = resolved(false);
        sessionProvider.signIn(reporter(REPORTER_ID));

        assertEquals(ALLOWED, policy.authorizeReopen(incident));
        assertEquals(ALLOWED, policy.authorizeReopen(incident, "The problem returned"));
        assertEquals(DENIED, policy.authorizeReopen(incident, " \n"));
        assertEquals(DENIED, policy.authorizeReopen(submitted(false), "Still broken"));

        sessionProvider.signIn(reporter(OTHER_REPORTER_ID));

        assertEquals(DENIED, policy.authorizeReopen(incident));
        assertEquals(DENIED, policy.authorizeReopen(incident, "Still broken"));
    }

    @Test
    void reporterIdentityStaysHiddenForAnonymousIncidents() {
        Incident identifying = submitted(false);
        Incident anonymous = submitted(true);
        sessionProvider.signIn(reporter(REPORTER_ID));

        assertEquals(ALLOWED, policy.authorizeViewReporterIdentity(anonymous));

        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.IT));

        assertEquals(ALLOWED, policy.authorizeViewReporterIdentity(identifying));
        assertEquals(DENIED, policy.authorizeViewReporterIdentity(anonymous));

        sessionProvider.signIn(administrator());

        assertEquals(ALLOWED, policy.authorizeViewReporterIdentity(identifying));
        assertEquals(DENIED, policy.authorizeViewReporterIdentity(anonymous));
    }

    @Test
    void eachDecisionReloadsCurrentRoleAndCategoryAccess() {
        Incident incident = submitted(false);
        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.IT));

        assertEquals(ALLOWED, policy.authorizeViewIncident(incident));

        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.FACILITIES));

        assertEquals(DENIED, policy.authorizeViewIncident(incident));
        assertEquals(DENIED, policy.authorizeClaim(incident));
    }

    @Test
    void signedOutAndDisabledAccountsAreDenied() {
        assertEquals(DENIED, policy.authorizeViewIncident(submitted(false)));

        sessionProvider.signIn(disabledResponder(RESPONDER_ID, IncidentCategory.IT));

        assertEquals(DENIED, policy.authorizeViewIncident(submitted(false)));
        assertEquals(DENIED, policy.authorizeClaim(submitted(false)));
    }
}

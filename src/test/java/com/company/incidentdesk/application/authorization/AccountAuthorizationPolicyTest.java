package com.company.incidentdesk.application.authorization;

import static com.company.incidentdesk.application.authorization.AuthorizationDecision.ALLOWED;
import static com.company.incidentdesk.application.authorization.AuthorizationDecision.DENIED;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.ADMIN_ID;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.REPORTER_ID;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.RESPONDER_ID;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.administrator;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.reporter;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.responder;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentCategory;

/** Tests every account and administration row of the authorization matrix. */
class AccountAuthorizationPolicyTest {
    private AuthorizationTestFixtures.MutableSessionProvider sessionProvider;
    private AccountAuthorizationPolicy policy;

    @BeforeEach
    void setUp() {
        sessionProvider = new AuthorizationTestFixtures.MutableSessionProvider();
        policy = new AccountAuthorizationPolicy(sessionProvider);
    }

    @Test
    void registrationCreatesReporterAccountsOnly() {
        assertEquals(ALLOWED, policy.authorizeRegistration(Role.REPORTER));
        assertEquals(DENIED, policy.authorizeRegistration(Role.RESPONDER));
        assertEquals(DENIED, policy.authorizeRegistration(Role.ADMINISTRATOR));
    }

    @Test
    void promotionRequestRequiresOwningReporter() {
        sessionProvider.signIn(reporter(REPORTER_ID));

        assertEquals(ALLOWED, policy.authorizePromotionRequest(REPORTER_ID));
        assertEquals(DENIED, policy.authorizePromotionRequest(RESPONDER_ID));

        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.IT));

        assertEquals(DENIED, policy.authorizePromotionRequest(RESPONDER_ID));

        sessionProvider.signIn(administrator());

        assertEquals(DENIED, policy.authorizePromotionRequest(ADMIN_ID));
    }

    @Test
    void onlyAdministratorCanManagePromotionsCategoriesSloAuditAndAccounts() {
        sessionProvider.signIn(administrator());

        assertAdministrativeDecisions(ALLOWED);

        sessionProvider.signIn(reporter(REPORTER_ID));

        assertAdministrativeDecisions(DENIED);

        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.IT));

        assertAdministrativeDecisions(DENIED);

        sessionProvider.signOut();

        assertAdministrativeDecisions(DENIED);
    }

    private void assertAdministrativeDecisions(AuthorizationDecision expected) {
        assertEquals(expected, policy.authorizePromotionDecision());
        assertEquals(expected, policy.authorizeCategoryAccessChange());
        assertEquals(expected, policy.authorizeSloConfiguration());
        assertEquals(expected, policy.authorizeAuditLog());
        assertEquals(expected, policy.authorizeDeleteOrResetAccount());
    }
}

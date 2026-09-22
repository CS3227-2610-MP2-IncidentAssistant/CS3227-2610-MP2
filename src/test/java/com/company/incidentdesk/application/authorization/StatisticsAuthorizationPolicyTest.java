package com.company.incidentdesk.application.authorization;

import static com.company.incidentdesk.application.authorization.AuthorizationDecision.ALLOWED;
import static com.company.incidentdesk.application.authorization.AuthorizationDecision.DENIED;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.OTHER_RESPONDER_ID;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.REPORTER_ID;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.RESPONDER_ID;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.administrator;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.reporter;
import static com.company.incidentdesk.application.authorization.AuthorizationTestFixtures.responder;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.incident.IncidentCategory;

/** Tests responder and administrator statistics authorization. */
class StatisticsAuthorizationPolicyTest {
    private AuthorizationTestFixtures.MutableSessionProvider sessionProvider;
    private StatisticsAuthorizationPolicy policy;

    @BeforeEach
    void setUp() {
        sessionProvider = new AuthorizationTestFixtures.MutableSessionProvider();
        policy = new StatisticsAuthorizationPolicy(sessionProvider);
    }

    @Test
    void responderCanViewOnlyOwnPerformance() {
        sessionProvider.signIn(responder(RESPONDER_ID, IncidentCategory.IT));

        assertEquals(ALLOWED, policy.authorizeResponderPerformance(RESPONDER_ID));
        assertEquals(DENIED, policy.authorizeResponderPerformance(OTHER_RESPONDER_ID));
        assertEquals(DENIED, policy.authorizeCompanyStatistics());
    }

    @Test
    void administratorCanViewAllStatistics() {
        sessionProvider.signIn(administrator());

        assertEquals(ALLOWED, policy.authorizeResponderPerformance(RESPONDER_ID));
        assertEquals(ALLOWED, policy.authorizeResponderPerformance(OTHER_RESPONDER_ID));
        assertEquals(ALLOWED, policy.authorizeCompanyStatistics());
    }

    @Test
    void reporterAndSignedOutActorCannotViewStatistics() {
        sessionProvider.signIn(reporter(REPORTER_ID));

        assertEquals(DENIED, policy.authorizeResponderPerformance(RESPONDER_ID));
        assertEquals(DENIED, policy.authorizeCompanyStatistics());

        sessionProvider.signOut();

        assertEquals(DENIED, policy.authorizeResponderPerformance(RESPONDER_ID));
        assertEquals(DENIED, policy.authorizeCompanyStatistics());
    }
}

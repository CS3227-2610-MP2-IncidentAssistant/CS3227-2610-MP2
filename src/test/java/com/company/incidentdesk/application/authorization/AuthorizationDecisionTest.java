package com.company.incidentdesk.application.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.result.OperationCompleted;

/** Tests presentation-safe conversion of authorization decisions. */
class AuthorizationDecisionTest {
    @Test
    void allowedDecisionProducesSuccess() {
        ApplicationResult<OperationCompleted> result = AuthorizationDecision.ALLOWED.toApplicationResult();

        assertTrue(result.isSuccess());
        assertEquals(OperationCompleted.INSTANCE, result.value().orElseThrow());
    }

    @Test
    void deniedDecisionProducesNeutralResourceUnavailableFailure() {
        ApplicationResult<OperationCompleted> result = AuthorizationDecision.DENIED.toApplicationResult();

        assertFalse(result.isSuccess());
        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, result.error().orElseThrow().code());
    }
}

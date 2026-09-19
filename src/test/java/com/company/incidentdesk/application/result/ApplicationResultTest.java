package com.company.incidentdesk.application.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests typed success and failure application results. */
class ApplicationResultTest {
    @Test
    void successExposesOnlyItsValue() {
        ApplicationResult<String> result = ApplicationResult.success("created");

        assertTrue(result.isSuccess());
        assertEquals("created", result.value().orElseThrow());
        assertTrue(result.error().isEmpty());
    }

    @Test
    void failureExposesOnlyItsError() {
        ApplicationError error = ApplicationError.of(ApplicationErrorCode.INVALID_STATE);
        ApplicationResult<String> result = ApplicationResult.failure(error);

        assertFalse(result.isSuccess());
        assertTrue(result.value().isEmpty());
        assertEquals(error, result.error().orElseThrow());
    }

    @Test
    void failureFactoryNeutralizesAccessDenial() {
        ApplicationError denied = ApplicationError.of(ApplicationErrorCode.ACCESS_DENIED);

        ApplicationResult<String> result = ApplicationResult.failure(denied);

        assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, result.error().orElseThrow().code());
    }

    @Test
    void resultPayloadsCannotBeNull() {
        assertThrows(NullPointerException.class, () -> ApplicationResult.success(null));
        assertThrows(NullPointerException.class, () -> ApplicationResult.failure(null));
    }

    @Test
    void commandCanReturnExplicitCompletionValue() {
        ApplicationResult<OperationCompleted> result = ApplicationResult.success(OperationCompleted.INSTANCE);

        assertEquals(OperationCompleted.INSTANCE, result.value().orElseThrow());
    }
}

package com.company.incidentdesk.application.authorization;

import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.result.OperationCompleted;

/** Deny-by-default outcome returned by shared authorization policies. */
public enum AuthorizationDecision {
    ALLOWED,
    DENIED;

    /**
     * Checks whether the requested operation is authorized.
     *
     * @return true only for an explicitly allowed operation
     */
    public boolean isAllowed() {
        return this == ALLOWED;
    }

    /**
     * Converts this decision into a presentation-safe application result.
     *
     * @return success or a neutral resource-unavailable failure
     */
    public ApplicationResult<OperationCompleted> toApplicationResult() {
        if (isAllowed()) {
            return ApplicationResult.success(OperationCompleted.INSTANCE);
        }
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.ACCESS_DENIED));
    }

    static AuthorizationDecision from(boolean allowed) {
        return allowed ? ALLOWED : DENIED;
    }
}

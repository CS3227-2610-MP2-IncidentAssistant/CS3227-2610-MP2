package com.company.incidentdesk.domain.incident;

import java.util.Objects;
import java.util.Set;

/** Indicates that a named lifecycle operation cannot run from the incident's current state. */
public final class InvalidIncidentTransitionException extends IllegalStateException {
    private final IncidentAction action;
    private final IncidentStatus actualStatus;
    private final Set<IncidentStatus> expectedStatuses;

    /**
     * Creates a stable invalid-transition failure.
     *
     * @param action attempted lifecycle action
     * @param actualStatus incident state at the time of the attempt
     * @param expectedStatuses states from which the action is allowed
     */
    public InvalidIncidentTransitionException(
            IncidentAction action,
            IncidentStatus actualStatus,
            Set<IncidentStatus> expectedStatuses) {
        super(message(action, actualStatus, expectedStatuses));
        this.action = Objects.requireNonNull(action, "action");
        this.actualStatus = Objects.requireNonNull(actualStatus, "actualStatus");
        this.expectedStatuses = Set.copyOf(Objects.requireNonNull(expectedStatuses, "expectedStatuses"));
    }

    public IncidentAction action() {
        return action;
    }

    public IncidentStatus actualStatus() {
        return actualStatus;
    }

    public Set<IncidentStatus> expectedStatuses() {
        return expectedStatuses;
    }

    private static String message(
            IncidentAction action,
            IncidentStatus actualStatus,
            Set<IncidentStatus> expectedStatuses) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actualStatus, "actualStatus");
        Objects.requireNonNull(expectedStatuses, "expectedStatuses");
        return action + " is not allowed from " + actualStatus + "; expected " + expectedStatuses;
    }
}

package com.company.incidentdesk.application.incident;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.company.incidentdesk.domain.audit.AuditChange;
import com.company.incidentdesk.domain.audit.AuditChangeField;
import com.company.incidentdesk.domain.incident.Incident;

/** Describes incident field changes without including report content in audit records. */
final class IncidentAuditChanges {
    private IncidentAuditChanges() {
    }

    static List<AuditChange> initialValues(Incident incident) {
        return List.of(
                AuditChange.added(AuditChangeField.INCIDENT_STATUS, incident.status().name()),
                AuditChange.added(AuditChangeField.CATEGORY, incident.category().name()),
                AuditChange.added(AuditChangeField.ANONYMOUS, Boolean.toString(incident.anonymous())));
    }

    static List<AuditChange> editChanges(Incident before, Incident after) {
        List<AuditChange> changes = new ArrayList<>();
        if (before.category() != after.category()) {
            changes.add(AuditChange.changed(AuditChangeField.CATEGORY, before.category().name(), after.category().name()));
        }
        if (before.anonymous() != after.anonymous()) {
            changes.add(AuditChange.changed(AuditChangeField.ANONYMOUS,
                    Boolean.toString(before.anonymous()), Boolean.toString(after.anonymous())));
        }
        if (changes.isEmpty()) {
            return List.of();
        }
        return List.copyOf(changes);
    }

    static List<AuditChange> statusChange(Incident before, Incident after) {
        return List.of(AuditChange.changed(
                AuditChangeField.INCIDENT_STATUS, before.status().name(), after.status().name()));
    }

    static List<AuditChange> assignmentChanges(Incident before, Incident after) {
        Optional<String> beforeAssignee = before.assigneeId().map(id -> id.value().toString());
        Optional<String> afterAssignee = after.assigneeId().map(id -> id.value().toString());
        return List.of(new AuditChange(AuditChangeField.ASSIGNEE_ID, beforeAssignee, afterAssignee));
    }

    static List<AuditChange> statusAndAssignmentChanges(Incident before, Incident after) {
        List<AuditChange> changes = new ArrayList<>(statusChange(before, after));
        if (!before.assigneeId().equals(after.assigneeId())) {
            changes.addAll(assignmentChanges(before, after));
        }
        return List.copyOf(changes);
    }
}

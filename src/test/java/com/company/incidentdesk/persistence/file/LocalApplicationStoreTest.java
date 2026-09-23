package com.company.incidentdesk.persistence.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.company.incidentdesk.application.incident.IncidentMutation;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActor;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditChange;
import com.company.incidentdesk.domain.audit.AuditChangeField;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.audit.AuditOutcome;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.domain.comment.CommentId;
import com.company.incidentdesk.domain.comment.CommentType;
import com.company.incidentdesk.domain.comment.IncidentComment;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.domain.incident.Resolution;
import com.company.incidentdesk.domain.incident.ResolutionCycle;
import com.company.incidentdesk.persistence.AuditQuery;
import com.company.incidentdesk.persistence.AuditSortDirection;
import com.company.incidentdesk.persistence.AuditedMutation;
import com.company.incidentdesk.persistence.IncidentQuery;
import com.company.incidentdesk.persistence.IncidentSort;
import com.company.incidentdesk.persistence.IncidentSortField;
import com.company.incidentdesk.persistence.SortDirection;

class LocalApplicationStoreTest {
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final AccountId REPORTER_ID = accountId(1);
    private static final AccountId RESPONDER_ID = accountId(2);

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsEveryIncidentLifecycleVariantAndRelationshipsAcrossRestart() {
        List<Incident> incidents = List.of(
                draft(10), submitted(11), assigned(12), resolved(13), withdrawn(14));
        AuditEvent audit = auditEvent(20, incidents.get(3).id(), AuditAction.INCIDENT_RESOLVED);
        IncidentComment comment = new IncidentComment(new CommentId(uuid(30)), incidents.get(3).id(),
                REPORTER_ID, Role.REPORTER, CREATED.plusSeconds(50), CommentType.ORDINARY, "Please clarify");

        try (LocalApplicationStore store = new LocalApplicationStore(temporaryDirectory)) {
            store.create(reporter());
            store.create(responder());
            for (Incident incident : incidents) {
                store.create(incident);
            }
            store.commit(new AuditedMutation<>(IncidentMutation.comment(incidents.get(3), comment), audit));
        }

        try (LocalApplicationStore reopened = new LocalApplicationStore(temporaryDirectory)) {
            assertEquals(List.of(reporter(), responder()), reopened.findAll());
            assertEquals(incidents, reopened.find(IncidentQuery.administratorAll(),
                    new IncidentSort(IncidentSortField.CREATED_AT, SortDirection.ASCENDING)));
            assertEquals(List.of(comment), reopened.findCommentsByIncidentId(incidents.get(3).id()));
            assertEquals(List.of(audit), reopened.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST));
        }
    }

    @Test
    void auditedIncidentMutationCommitsDomainCommentAndAuditTogether() {
        Incident incident = submitted(40);
        IncidentComment comment = new IncidentComment(new CommentId(uuid(41)), incident.id(), REPORTER_ID,
                Role.REPORTER, CREATED.plusSeconds(5), CommentType.ORDINARY, "Update");
        AuditEvent event = auditEvent(42, incident.id(), AuditAction.COMMENT_ADDED);

        try (LocalApplicationStore store = new LocalApplicationStore(temporaryDirectory)) {
            store.create(reporter());
            store.commit(new AuditedMutation<>(IncidentMutation.create(incident),
                    auditEvent(43, incident.id(), AuditAction.INCIDENT_CREATED)));
            store.commit(new AuditedMutation<>(IncidentMutation.comment(incident, comment), event));
        }

        try (LocalApplicationStore reopened = new LocalApplicationStore(temporaryDirectory)) {
            assertEquals(incident, reopened.findById(incident.id()).orElseThrow());
            assertEquals(List.of(comment), reopened.findCommentsByIncidentId(incident.id()));
            assertEquals(event, reopened.findById(event.id()).orElseThrow());
        }
    }

    @Test
    void explicitBackupRestoreRecoversPreviousKnownGoodCommit() {
        try (LocalApplicationStore store = new LocalApplicationStore(temporaryDirectory)) {
            store.create(reporter());
            store.create(responder());
            assertTrue(store.findById(RESPONDER_ID).isPresent());

            store.restoreLastKnownGoodBackup();

            assertTrue(store.findById(REPORTER_ID).isPresent());
            assertTrue(store.findById(RESPONDER_ID).isEmpty());
        }
    }

    @Test
    void offlineRecoveryRestoresBackupAndPreservesCorruptCanonical() throws Exception {
        try (LocalApplicationStore store = new LocalApplicationStore(temporaryDirectory)) {
            store.create(reporter());
            store.create(responder());
        }
        Path canonical = temporaryDirectory.resolve(LocalApplicationStore.STATE_FILE_NAME);
        Files.writeString(canonical, "damaged");

        LocalApplicationStore.recoverLastKnownGoodBackup(temporaryDirectory);

        assertEquals("damaged", Files.readString(canonical.resolveSibling("incident-desk.dat.corrupt")));
        try (LocalApplicationStore recovered = new LocalApplicationStore(temporaryDirectory)) {
            assertTrue(recovered.findById(REPORTER_ID).isPresent());
            assertTrue(recovered.findById(RESPONDER_ID).isEmpty());
        }
    }

    private static Account reporter() {
        return new Account(REPORTER_ID, "reporter", Role.REPORTER, AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    private static Account responder() {
        return new Account(RESPONDER_ID, "responder", Role.RESPONDER, AccountStatus.ENABLED,
                ResponderAccess.to(Set.of(IncidentCategory.IT)));
    }

    private static Incident draft(long id) {
        return new Incident(new IncidentId(uuid(id)), REPORTER_ID, "Draft", "", IncidentCategory.IT,
                IncidentStatus.DRAFT, false, CREATED, Optional.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Incident submitted(long id) {
        Instant submitted = CREATED.plusSeconds(id);
        return new Incident(new IncidentId(uuid(id)), REPORTER_ID, "Submitted", "Description", IncidentCategory.IT,
                IncidentStatus.SUBMITTED, true, CREATED, Optional.of(submitted), Optional.empty(), Optional.empty(),
                List.of(new ResolutionCycle(submitted, Optional.empty(), Optional.empty(), Optional.empty())));
    }

    private static Incident assigned(long id) {
        Instant submitted = CREATED.plusSeconds(id);
        Instant assigned = submitted.plusSeconds(1);
        return new Incident(new IncidentId(uuid(id)), REPORTER_ID, "Assigned", "Description", IncidentCategory.IT,
                IncidentStatus.ASSIGNED, false, CREATED, Optional.of(submitted), Optional.empty(),
                Optional.of(RESPONDER_ID), List.of(new ResolutionCycle(submitted, Optional.of(assigned),
                        Optional.of(assigned), Optional.empty())));
    }

    private static Incident resolved(long id) {
        Instant submitted = CREATED.plusSeconds(id);
        Instant assigned = submitted.plusSeconds(1);
        Resolution resolution = new Resolution("Fixed", assigned.plusSeconds(1), RESPONDER_ID, RESPONDER_ID);
        return new Incident(new IncidentId(uuid(id)), REPORTER_ID, "Resolved", "Description", IncidentCategory.IT,
                IncidentStatus.RESOLVED, false, CREATED, Optional.of(submitted), Optional.empty(), Optional.empty(),
                List.of(new ResolutionCycle(submitted, Optional.of(assigned), Optional.of(assigned),
                        Optional.of(resolution))));
    }

    private static Incident withdrawn(long id) {
        Instant submitted = CREATED.plusSeconds(id);
        return new Incident(new IncidentId(uuid(id)), REPORTER_ID, "Withdrawn", "Description",
                IncidentCategory.FACILITIES, IncidentStatus.WITHDRAWN, false, CREATED, Optional.of(submitted),
                Optional.of(submitted.plusSeconds(1)), Optional.empty(),
                List.of(new ResolutionCycle(submitted, Optional.empty(), Optional.empty(), Optional.empty())));
    }

    private static AuditEvent auditEvent(long id, IncidentId incidentId, AuditAction action) {
        return new AuditEvent(new AuditEventId(uuid(id)), CREATED.plusSeconds(id),
                new AuditActor(REPORTER_ID, Role.REPORTER, AuditActorVisibility.STANDARD), action,
                new AuditTarget(AuditTargetType.INCIDENT, incidentId.value().toString()), AuditOutcome.SUCCESS,
                List.of(AuditChange.added(AuditChangeField.INCIDENT_STATUS, "RESOLVED")), Optional.empty());
    }

    private static AccountId accountId(long value) {
        return new AccountId(uuid(value));
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}

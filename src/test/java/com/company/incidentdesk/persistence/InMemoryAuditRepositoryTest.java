package com.company.incidentdesk.persistence;

import static com.company.incidentdesk.testutil.AuditTestData.ACTOR_ID;
import static com.company.incidentdesk.testutil.AuditTestData.INCIDENT_TARGET;
import static com.company.incidentdesk.testutil.AuditTestData.OCCURRED_AT;
import static com.company.incidentdesk.testutil.AuditTestData.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditTarget;
import com.company.incidentdesk.domain.audit.AuditTargetType;
import com.company.incidentdesk.persistence.memory.InMemoryAuditRepository;

/** Tests append-only audit storage, filters, privacy, and deterministic ordering. */
class InMemoryAuditRepositoryTest {
    private static final String FIRST_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SECOND_ID = "00000000-0000-0000-0000-000000000002";
    private static final String THIRD_ID = "00000000-0000-0000-0000-000000000003";
    private static final AuditTarget ACCOUNT_TARGET = new AuditTarget(
            AuditTargetType.ACCOUNT,
            ACTOR_ID.value().toString());

    @Test
    void appendsAndFindsEventsWithoutUpdateOrDeleteOperations() {
        InMemoryAuditRepository repository = new InMemoryAuditRepository();
        AuditEvent auditEvent = event(FIRST_ID, OCCURRED_AT);

        repository.append(auditEvent);

        assertEquals(auditEvent, repository.findById(auditEvent.id()).orElseThrow());
        assertEquals(List.of(auditEvent), repository.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST));
        assertThrows(
                UnsupportedOperationException.class,
                () -> repository.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).clear());
    }

    @Test
    void duplicateAppendFailsWithoutReplacingOriginalEvent() {
        InMemoryAuditRepository repository = new InMemoryAuditRepository();
        AuditEvent original = event(FIRST_ID, OCCURRED_AT);
        AuditEvent duplicate = event(
                FIRST_ID,
                OCCURRED_AT.plusSeconds(1),
                AuditActorVisibility.STANDARD,
                AuditAction.ACCOUNT_DISABLED,
                ACCOUNT_TARGET);
        repository.append(original);

        RepositoryException failure = assertThrows(
                RepositoryException.class,
                () -> repository.append(duplicate));

        assertEquals(StorageFailureCode.ALREADY_EXISTS, failure.code());
        assertEquals(original, repository.findById(original.id()).orElseThrow());
    }

    @Test
    void sortsEqualTimestampsByEventIdentifier() {
        InMemoryAuditRepository repository = new InMemoryAuditRepository();
        AuditEvent second = event(SECOND_ID, OCCURRED_AT);
        AuditEvent first = event(FIRST_ID, OCCURRED_AT);
        repository.append(second);
        repository.append(first);

        assertEquals(
                List.of(first, second),
                repository.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST));
        assertEquals(
                List.of(second, first),
                repository.find(AuditQuery.all(), AuditSortDirection.NEWEST_FIRST));
    }

    @Test
    void filtersByTimeActionAndTarget() {
        InMemoryAuditRepository repository = populatedRepository();
        Instant middleTime = OCCURRED_AT.plusSeconds(1);
        AuditQuery query = new AuditQuery(
                Optional.of(middleTime),
                Optional.of(middleTime.plusSeconds(1)),
                Optional.empty(),
                Set.of(AuditAction.ACCOUNT_DISABLED),
                Optional.of(ACCOUNT_TARGET));

        List<AuditEvent> results = repository.find(query, AuditSortDirection.OLDEST_FIRST);

        assertEquals(List.of(SECOND_ID), identifiers(results));
    }

    @Test
    void incidentTargetQueryReturnsOnlyThatIncidentHistory() {
        InMemoryAuditRepository repository = populatedRepository();

        List<AuditEvent> results = repository.find(
                AuditQuery.forTarget(INCIDENT_TARGET),
                AuditSortDirection.OLDEST_FIRST);

        assertEquals(List.of(FIRST_ID, THIRD_ID), identifiers(results));
    }

    @Test
    void actorFilterDoesNotRevealAnonymousReporterEvents() {
        InMemoryAuditRepository repository = populatedRepository();
        AuditQuery actorQuery = new AuditQuery(
                Optional.empty(),
                Optional.empty(),
                Optional.of(ACTOR_ID),
                Set.of(),
                Optional.empty());

        List<AuditEvent> results = repository.find(actorQuery, AuditSortDirection.OLDEST_FIRST);

        assertEquals(List.of(FIRST_ID, SECOND_ID), identifiers(results));
    }

    @Test
    void rejectsEmptyOrBackwardsTimeRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditQuery(
                        Optional.of(OCCURRED_AT),
                        Optional.of(OCCURRED_AT),
                        Optional.empty(),
                        Set.of(),
                        Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuditQuery(
                        Optional.of(OCCURRED_AT.plusSeconds(1)),
                        Optional.of(OCCURRED_AT),
                        Optional.empty(),
                        Set.of(),
                        Optional.empty()));
    }

    private static InMemoryAuditRepository populatedRepository() {
        InMemoryAuditRepository repository = new InMemoryAuditRepository();
        repository.append(event(FIRST_ID, OCCURRED_AT));
        repository.append(event(
                SECOND_ID,
                OCCURRED_AT.plusSeconds(1),
                AuditActorVisibility.STANDARD,
                AuditAction.ACCOUNT_DISABLED,
                ACCOUNT_TARGET));
        repository.append(event(
                THIRD_ID,
                OCCURRED_AT.plusSeconds(2),
                AuditActorVisibility.ANONYMOUS_REPORTER,
                AuditAction.COMMENT_ADDED,
                INCIDENT_TARGET));
        return repository;
    }

    private static List<String> identifiers(List<AuditEvent> events) {
        return events.stream().map(auditEvent -> auditEvent.id().value().toString()).toList();
    }
}

package com.company.incidentdesk.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.persistence.memory.InMemoryIncidentRepository;

/** Contract and role-query tests for the in-memory incident repository. */
class InMemoryIncidentRepositoryTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-19T08:00:00Z");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-19T08:01:00Z");
    private static final AccountId REPORTER_ID = accountId("f24a7d0a-7c48-4703-b9d7-a39c933149a1");
    private static final AccountId OTHER_REPORTER_ID = accountId("7a759125-09d5-4509-81bc-93d26078aa39");
    private static final AccountId RESPONDER_ID = accountId("f2e6c9a8-c1db-4d9d-ad8a-22a342043835");
    private static final AccountId OTHER_RESPONDER_ID = accountId("2bf27a40-726d-423c-ad19-bc781426b2be");
    private static final IncidentId FIRST_ID = incidentId("00000000-0000-0000-0000-000000000001");
    private static final IncidentId SECOND_ID = incidentId("00000000-0000-0000-0000-000000000002");
    private static final IncidentId THIRD_ID = incidentId("00000000-0000-0000-0000-000000000003");
    private static final IncidentId FOURTH_ID = incidentId("00000000-0000-0000-0000-000000000004");

    @Test
    void createAndUpdateAreDistinctAndLeaveExistingDataOnFailure() {
        InMemoryIncidentRepository repository = new InMemoryIncidentRepository();
        Incident submitted = submitted(FIRST_ID, REPORTER_ID, IncidentCategory.IT, "First", CREATED_AT);
        repository.create(submitted);

        RepositoryException duplicate = assertThrows(
                RepositoryException.class,
                () -> repository.create(submitted));
        assertEquals(StorageFailureCode.ALREADY_EXISTS, duplicate.code());
        assertEquals(submitted, repository.findById(FIRST_ID).orElseThrow());

        Incident assigned = lifecycleAt(ASSIGNED_AT).claim(submitted, RESPONDER_ID);
        repository.update(assigned);
        assertEquals(assigned, repository.findById(FIRST_ID).orElseThrow());

        Incident missing = submitted(SECOND_ID, REPORTER_ID, IncidentCategory.IT, "Missing", CREATED_AT);
        RepositoryException notFound = assertThrows(
                RepositoryException.class,
                () -> repository.update(missing));
        assertEquals(StorageFailureCode.NOT_FOUND, notFound.code());
        assertTrue(repository.findById(SECOND_ID).isEmpty());
    }

    @Test
    void reporterOwnedQueryReturnsEveryLifecycleStateForThatReporter() {
        InMemoryIncidentRepository repository = populatedRepository();

        List<Incident> results = repository.find(
                IncidentQuery.reporterOwned(REPORTER_ID),
                new IncidentSort(IncidentSortField.TITLE, SortDirection.ASCENDING));

        assertEquals(List.of(FIRST_ID, THIRD_ID), ids(results));
    }

    @Test
    void responderEligibleQueryReturnsOnlyUnassignedSubmittedPermittedCategories() {
        InMemoryIncidentRepository repository = populatedRepository();

        List<Incident> results = repository.find(
                IncidentQuery.responderEligibleUnassigned(Set.of(IncidentCategory.IT)),
                IncidentSort.queueOrder());

        assertEquals(List.of(FIRST_ID), ids(results));
        assertTrue(repository.find(
                IncidentQuery.responderEligibleUnassigned(Set.of()),
                IncidentSort.queueOrder()).isEmpty());
    }

    @Test
    void responderAssignedQueryReturnsOnlyCurrentRespondersAssignments() {
        InMemoryIncidentRepository repository = populatedRepository();

        List<Incident> ownAssignments = repository.find(
                IncidentQuery.responderAssigned(RESPONDER_ID),
                IncidentSort.queueOrder());
        List<Incident> otherAssignments = repository.find(
                IncidentQuery.responderAssigned(OTHER_RESPONDER_ID),
                IncidentSort.queueOrder());

        assertEquals(List.of(THIRD_ID), ids(ownAssignments));
        assertEquals(List.of(FOURTH_ID), ids(otherAssignments));
    }

    @Test
    void administratorQueryReturnsAllIncidentsWithoutApplyingAuthorization() {
        InMemoryIncidentRepository repository = populatedRepository();

        List<Incident> results = repository.find(
                IncidentQuery.administratorAll(),
                new IncidentSort(IncidentSortField.CREATED_AT, SortDirection.ASCENDING));

        assertEquals(List.of(FIRST_ID, SECOND_ID, THIRD_ID, FOURTH_ID), ids(results));
        assertThrows(UnsupportedOperationException.class, () -> results.clear());
    }

    @Test
    void equalPrimarySortValuesUseAscendingIdentifierTieBreaker() {
        InMemoryIncidentRepository repository = new InMemoryIncidentRepository();
        Incident second = submitted(SECOND_ID, REPORTER_ID, IncidentCategory.IT, "Same", CREATED_AT);
        Incident first = submitted(FIRST_ID, REPORTER_ID, IncidentCategory.IT, "Same", CREATED_AT);
        repository.create(second);
        repository.create(first);

        List<Incident> ascending = repository.find(
                IncidentQuery.administratorAll(),
                new IncidentSort(IncidentSortField.TITLE, SortDirection.ASCENDING));
        List<Incident> descending = repository.find(
                IncidentQuery.administratorAll(),
                new IncidentSort(IncidentSortField.TITLE, SortDirection.DESCENDING));

        assertEquals(List.of(FIRST_ID, SECOND_ID), ids(ascending));
        assertEquals(List.of(FIRST_ID, SECOND_ID), ids(descending));
    }

    @Test
    void missingLifecycleTimestampsRemainLastInEitherDirection() {
        InMemoryIncidentRepository repository = new InMemoryIncidentRepository();
        Incident draft = draft(FIRST_ID, REPORTER_ID, "Draft", CREATED_AT);
        Incident submitted = submitted(
                SECOND_ID,
                REPORTER_ID,
                IncidentCategory.IT,
                "Submitted",
                CREATED_AT.plusSeconds(1));
        repository.create(draft);
        repository.create(submitted);

        for (SortDirection direction : SortDirection.values()) {
            List<Incident> results = repository.find(
                    IncidentQuery.administratorAll(),
                    new IncidentSort(IncidentSortField.QUEUE_ENTERED_AT, direction));
            assertEquals(List.of(SECOND_ID, FIRST_ID), ids(results));
        }
    }

    @Test
    void queryCopiesCategoriesAndRejectsFieldsForWrongScope() {
        java.util.HashSet<IncidentCategory> categories = new java.util.HashSet<>();
        categories.add(IncidentCategory.IT);
        IncidentQuery query = IncidentQuery.responderEligibleUnassigned(categories);
        categories.clear();

        assertEquals(Set.of(IncidentCategory.IT), query.categories());
        assertThrows(UnsupportedOperationException.class, () -> query.categories().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new IncidentQuery(
                        IncidentQueryScope.ADMINISTRATOR_ALL,
                        Optional.of(REPORTER_ID),
                        Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IncidentQuery(
                        IncidentQueryScope.REPORTER_OWNED,
                        Optional.empty(),
                        Set.of()));
    }

    private static InMemoryIncidentRepository populatedRepository() {
        InMemoryIncidentRepository repository = new InMemoryIncidentRepository();
        Incident reporterQueue = submitted(
                FIRST_ID,
                REPORTER_ID,
                IncidentCategory.IT,
                "A queue incident",
                CREATED_AT);
        Incident otherQueue = submitted(
                SECOND_ID,
                OTHER_REPORTER_ID,
                IncidentCategory.FACILITIES,
                "B other queue",
                CREATED_AT);
        Incident ownAssignment = lifecycleAt(ASSIGNED_AT).claim(
                submitted(
                        THIRD_ID,
                        REPORTER_ID,
                        IncidentCategory.IT,
                        "C own assignment",
                        CREATED_AT),
                RESPONDER_ID);
        Incident otherAssignment = lifecycleAt(ASSIGNED_AT).claim(
                submitted(
                        FOURTH_ID,
                        OTHER_REPORTER_ID,
                        IncidentCategory.IT,
                        "D other assignment",
                        CREATED_AT),
                OTHER_RESPONDER_ID);
        repository.create(reporterQueue);
        repository.create(otherQueue);
        repository.create(ownAssignment);
        repository.create(otherAssignment);
        return repository;
    }

    private static Incident submitted(
            IncidentId id,
            AccountId reporterId,
            IncidentCategory category,
            String title,
            Instant submittedAt) {
        return lifecycleAt(submittedAt).submit(
                id,
                reporterId,
                title,
                "Incident description",
                category,
                false);
    }

    private static Incident draft(IncidentId id, AccountId reporterId, String title, Instant createdAt) {
        return lifecycleAt(createdAt).saveDraft(
                id,
                reporterId,
                title,
                "Incident description",
                IncidentCategory.IT,
                false);
    }

    private static List<IncidentId> ids(List<Incident> incidents) {
        return incidents.stream().map(Incident::id).toList();
    }

    private static IncidentLifecycle lifecycleAt(Instant instant) {
        return new IncidentLifecycle(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static AccountId accountId(String value) {
        return new AccountId(UUID.fromString(value));
    }

    private static IncidentId incidentId(String value) {
        return new IncidentId(UUID.fromString(value));
    }
}

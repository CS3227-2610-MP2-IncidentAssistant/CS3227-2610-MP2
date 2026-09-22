package com.company.incidentdesk.persistence;

import static com.company.incidentdesk.testutil.AuditTestData.OCCURRED_AT;
import static com.company.incidentdesk.testutil.AuditTestData.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.persistence.memory.InMemoryAuditedMutationStore;

/** Tests the all-or-nothing state and audit commit boundary. */
class InMemoryAuditedMutationStoreTest {
    private static final String EVENT_ID = "00000000-0000-0000-0000-000000000001";

    @Test
    void successfulCommitReplacesStateAndAppendsAuditTogether() {
        TestState initial = new TestState(0);
        TestState next = new TestState(1);
        AuditEvent auditEvent = event(EVENT_ID, OCCURRED_AT);
        InMemoryAuditedMutationStore<TestState> store = new InMemoryAuditedMutationStore<>(initial);

        store.commit(new AuditedMutation<>(next, auditEvent));

        assertEquals(next, store.currentState());
        assertEquals(java.util.List.of(auditEvent), store.auditEvents());
        assertThrows(UnsupportedOperationException.class, () -> store.auditEvents().clear());
    }

    @Test
    void preparationFailureCommitsNeitherStateNorAudit() {
        TestState initial = new TestState(0);
        InMemoryAuditedMutationStore<TestState> store = new InMemoryAuditedMutationStore<>(
                initial,
                ignored -> {
                    throw new RepositoryException(
                            StorageFailureCode.STORAGE_UNAVAILABLE,
                            "simulated persistence failure");
                });

        RepositoryException failure = assertThrows(
                RepositoryException.class,
                () -> store.commit(new AuditedMutation<>(
                        new TestState(1),
                        event(EVENT_ID, OCCURRED_AT))));

        assertEquals(StorageFailureCode.STORAGE_UNAVAILABLE, failure.code());
        assertEquals(initial, store.currentState());
        assertTrue(store.auditEvents().isEmpty());
    }

    @Test
    void duplicateAuditIdentifierCommitsNeitherReplacementStateNorEvent() {
        AuditEvent auditEvent = event(EVENT_ID, OCCURRED_AT);
        TestState committed = new TestState(1);
        InMemoryAuditedMutationStore<TestState> store = new InMemoryAuditedMutationStore<>(new TestState(0));
        store.commit(new AuditedMutation<>(committed, auditEvent));

        RepositoryException failure = assertThrows(
                RepositoryException.class,
                () -> store.commit(new AuditedMutation<>(new TestState(2), auditEvent)));

        assertEquals(StorageFailureCode.ALREADY_EXISTS, failure.code());
        assertEquals(committed, store.currentState());
        assertEquals(java.util.List.of(auditEvent), store.auditEvents());
    }

    private record TestState(int version) {
    }
}

package com.company.incidentdesk.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.persistence.memory.InMemoryAttachmentMetadataRepository;
import com.company.incidentdesk.persistence.memory.InMemoryCommentRepository;
import com.company.incidentdesk.persistence.memory.InMemoryPromotionRequestRepository;
import com.company.incidentdesk.persistence.memory.InMemorySloConfigurationRepository;

/** Shared create/update and append-only contract tests for generic in-memory adapters. */
class InMemoryGenericRepositoryContractTest {
    private static final IncidentId INCIDENT_ID = new IncidentId(
            java.util.UUID.fromString("61e27e29-7a71-405a-a1a0-24b14e4de0fb"));

    @Test
    void mutableRepositoriesSeparateCreateFromUpdate() {
        List<Supplier<MutableRepository<String, String>>> repositories = List.of(
                InMemoryPromotionRequestRepository::new,
                () -> new InMemoryAttachmentMetadataRepository<>(ignored -> INCIDENT_ID));

        for (Supplier<MutableRepository<String, String>> factory : repositories) {
            verifyMutableContract(factory.get());
        }
    }

    @Test
    void appendOnlyRepositoriesRejectDuplicateIdentifiersAndExposeNoUpdate() {
        List<Supplier<AppendOnlyRepository<String, String>>> repositories = List.of(
                () -> new InMemoryCommentRepository<>(ignored -> INCIDENT_ID),
                () -> new InMemorySloConfigurationRepository<>(ignored -> IncidentCategory.IT));

        for (Supplier<AppendOnlyRepository<String, String>> factory : repositories) {
            verifyAppendOnlyContract(factory.get());
        }
    }

    @Test
    void relatedRecordRepositoriesFilterByIncidentOrCategory() {
        InMemoryCommentRepository<String, RelatedRecord> comments =
                new InMemoryCommentRepository<>(RelatedRecord::incidentId);
        InMemoryAttachmentMetadataRepository<String, RelatedRecord> attachments =
                new InMemoryAttachmentMetadataRepository<>(RelatedRecord::incidentId);
        IncidentId otherIncidentId = new IncidentId(
                java.util.UUID.fromString("d88f29f6-40ad-46d6-86e0-723d36c64092"));
        RelatedRecord matching = new RelatedRecord(INCIDENT_ID, "matching");
        RelatedRecord other = new RelatedRecord(otherIncidentId, "other");

        comments.append("comment-1", matching);
        comments.append("comment-2", other);
        attachments.create("attachment-1", matching);
        attachments.create("attachment-2", other);

        assertEquals(List.of(matching), comments.findByIncidentId(INCIDENT_ID));
        assertEquals(List.of(matching), attachments.findByIncidentId(INCIDENT_ID));

        InMemorySloConfigurationRepository<String, CategoryRecord> configurations =
                new InMemorySloConfigurationRepository<>(CategoryRecord::category);
        CategoryRecord it = new CategoryRecord(IncidentCategory.IT, "it");
        CategoryRecord facilities = new CategoryRecord(IncidentCategory.FACILITIES, "facilities");
        configurations.append("slo-1", it);
        configurations.append("slo-2", facilities);

        assertEquals(List.of(it), configurations.findByCategory(IncidentCategory.IT));
    }

    private static void verifyMutableContract(MutableRepository<String, String> repository) {
        repository.create("id", "first");
        assertEquals("first", repository.findById("id").orElseThrow());

        RepositoryException duplicate = assertThrows(
                RepositoryException.class,
                () -> repository.create("id", "duplicate"));
        assertEquals(StorageFailureCode.ALREADY_EXISTS, duplicate.code());
        assertEquals("first", repository.findById("id").orElseThrow());

        repository.update("id", "updated");
        assertEquals("updated", repository.findById("id").orElseThrow());

        RepositoryException missing = assertThrows(
                RepositoryException.class,
                () -> repository.update("missing", "value"));
        assertEquals(StorageFailureCode.NOT_FOUND, missing.code());
        assertTrue(repository.findById("missing").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> repository.findAll().clear());
    }

    private static void verifyAppendOnlyContract(AppendOnlyRepository<String, String> repository) {
        repository.append("id", "first");
        assertEquals("first", repository.findById("id").orElseThrow());

        RepositoryException duplicate = assertThrows(
                RepositoryException.class,
                () -> repository.append("id", "duplicate"));
        assertEquals(StorageFailureCode.ALREADY_EXISTS, duplicate.code());
        assertEquals(List.of("first"), repository.findAll());
        assertThrows(UnsupportedOperationException.class, () -> repository.findAll().clear());
    }

    private record RelatedRecord(IncidentId incidentId, String value) {
    }

    private record CategoryRecord(IncidentCategory category, String value) {
    }
}

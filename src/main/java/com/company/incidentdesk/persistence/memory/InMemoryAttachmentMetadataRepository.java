package com.company.incidentdesk.persistence.memory;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.persistence.AttachmentMetadataRepository;

/** In-memory attachment-metadata repository parameterized by the future metadata type. */
public final class InMemoryAttachmentMetadataRepository<I, M>
        extends InMemoryMutableRepository<I, M>
        implements AttachmentMetadataRepository<I, M> {
    private final Function<M, IncidentId> incidentIdExtractor;

    public InMemoryAttachmentMetadataRepository(Function<M, IncidentId> incidentIdExtractor) {
        this.incidentIdExtractor = Objects.requireNonNull(incidentIdExtractor, "incidentIdExtractor");
    }

    @Override
    public List<M> findByIncidentId(IncidentId incidentId) {
        IncidentId requiredId = Objects.requireNonNull(incidentId, "incidentId");
        return findAll().stream()
                .filter(metadata -> incidentIdExtractor.apply(metadata).equals(requiredId))
                .toList();
    }
}

package com.company.incidentdesk.persistence.memory;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.persistence.CommentRepository;

/** In-memory comment repository parameterized by the future comment domain type. */
public final class InMemoryCommentRepository<I, C>
        extends InMemoryAppendOnlyRepository<I, C>
        implements CommentRepository<I, C> {
    private final Function<C, IncidentId> incidentIdExtractor;

    public InMemoryCommentRepository(Function<C, IncidentId> incidentIdExtractor) {
        this.incidentIdExtractor = Objects.requireNonNull(incidentIdExtractor, "incidentIdExtractor");
    }

    @Override
    public List<C> findByIncidentId(IncidentId incidentId) {
        IncidentId requiredId = Objects.requireNonNull(incidentId, "incidentId");
        return findAll().stream()
                .filter(comment -> incidentIdExtractor.apply(comment).equals(requiredId))
                .toList();
    }
}

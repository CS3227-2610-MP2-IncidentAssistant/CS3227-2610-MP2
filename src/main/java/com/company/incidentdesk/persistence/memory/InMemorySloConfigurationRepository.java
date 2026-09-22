package com.company.incidentdesk.persistence.memory;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.persistence.SloConfigurationRepository;

/** In-memory append-only repository parameterized by the future SLO configuration type. */
public final class InMemorySloConfigurationRepository<I, S>
        extends InMemoryAppendOnlyRepository<I, S>
        implements SloConfigurationRepository<I, S> {
    private final Function<S, IncidentCategory> categoryExtractor;

    public InMemorySloConfigurationRepository(Function<S, IncidentCategory> categoryExtractor) {
        this.categoryExtractor = Objects.requireNonNull(categoryExtractor, "categoryExtractor");
    }

    @Override
    public List<S> findByCategory(IncidentCategory category) {
        IncidentCategory requiredCategory = Objects.requireNonNull(category, "category");
        return findAll().stream()
                .filter(configuration -> categoryExtractor.apply(configuration) == requiredCategory)
                .toList();
    }
}

package com.company.incidentdesk.domain.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** Tests the product's initial incident categories. */
class IncidentCategoryTest {
    @Test
    void containsTheInitialProductCategories() {
        assertEquals(
                Set.of("IT", "Human Relations", "Facilities"),
                Set.of(
                        IncidentCategory.IT.displayName(),
                        IncidentCategory.HUMAN_RELATIONS.displayName(),
                        IncidentCategory.FACILITIES.displayName()));
    }
}

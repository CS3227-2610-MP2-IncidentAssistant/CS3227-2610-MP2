package com.company.incidentdesk.domain.account;

import java.util.Objects;
import java.util.Set;

import com.company.incidentdesk.domain.incident.IncidentCategory;

/** Immutable set of incident categories assigned to a responder. */
public record ResponderAccess(Set<IncidentCategory> categories) {
    /** No responder-category access. */
    public static final ResponderAccess NONE = new ResponderAccess(Set.of());

    /**
     * Creates responder access from the supplied categories.
     *
     * @param categories assigned incident categories
     */
    public ResponderAccess {
        Objects.requireNonNull(categories, "categories");
        categories = Set.copyOf(categories);
    }

    /**
     * Creates access to the supplied categories.
     *
     * @param categories assigned incident categories
     * @return immutable responder access
     */
    public static ResponderAccess to(Set<IncidentCategory> categories) {
        return new ResponderAccess(categories);
    }

    /**
     * Checks whether the responder may handle a category.
     *
     * @param category category to check
     * @return true when the category is assigned
     */
    public boolean permits(IncidentCategory category) {
        return categories.contains(Objects.requireNonNull(category, "category"));
    }

    /**
     * Checks whether no categories are assigned.
     *
     * @return true when the access set is empty
     */
    public boolean isEmpty() {
        return categories.isEmpty();
    }
}

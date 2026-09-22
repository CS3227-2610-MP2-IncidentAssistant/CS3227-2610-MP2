package com.company.incidentdesk.persistence.memory;

import com.company.incidentdesk.persistence.PromotionRequestRepository;

/** In-memory repository parameterized by the future promotion-request type. */
public final class InMemoryPromotionRequestRepository<I, P>
        extends InMemoryMutableRepository<I, P>
        implements PromotionRequestRepository<I, P> {
}

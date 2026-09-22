package com.company.incidentdesk.persistence.memory;

import com.company.incidentdesk.persistence.AuditRepository;

/** In-memory append-only repository parameterized by the future audit event type. */
public final class InMemoryAuditRepository<I, A>
        extends InMemoryAppendOnlyRepository<I, A>
        implements AuditRepository<I, A> {
}

package com.company.incidentdesk.persistence;

/** Append-only repository contract for future audit event records. */
public interface AuditRepository<I, A> extends AppendOnlyRepository<I, A> {
}

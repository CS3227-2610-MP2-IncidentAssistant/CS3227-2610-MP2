package com.company.incidentdesk.persistence;

import java.util.List;

import com.company.incidentdesk.domain.incident.IncidentId;

/** Repository contract for future attachment metadata records, excluding file content. */
public interface AttachmentMetadataRepository<I, M> extends MutableRepository<I, M> {
    List<M> findByIncidentId(IncidentId incidentId);
}

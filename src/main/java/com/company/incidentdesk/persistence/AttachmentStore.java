package com.company.incidentdesk.persistence;

import java.util.List;
import java.util.Optional;

import com.company.incidentdesk.application.attachment.AttachmentLimits;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.attachment.AttachmentId;
import com.company.incidentdesk.domain.attachment.IncidentAttachment;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentId;

/** Attachment facet; additions include immutable file content and atomic audit metadata. */
public interface AttachmentStore {
    List<IncidentAttachment> list(IncidentId incidentId);
    Optional<IncidentAttachment> find(AttachmentId id);
    byte[] read(IncidentAttachment attachment);
    String mediaSource(IncidentAttachment attachment);
    void add(IncidentAttachment attachment, byte[] content, Incident expectedIncident, Account expectedActor,
            AttachmentLimits limits, AuditEvent audit, AuditEvent migrationAudit);
}

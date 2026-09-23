package com.company.incidentdesk.persistence.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.domain.slo.SloTargetVersionId;
import com.company.incidentdesk.persistence.AuditedMutation;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.SloConfigurationStore;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** In-memory SLO configuration history with atomic version-and-audit commits for tests. */
public final class InMemorySloConfigurationStore implements SloConfigurationStore {
    private final InMemorySloConfigurationRepository<SloTargetVersionId, SloTargetVersion> versions =
            new InMemorySloConfigurationRepository<>(SloTargetVersion::category);
    private List<AuditEvent> auditEvents = List.of();

    @Override
    public void append(SloTargetVersionId id, SloTargetVersion value) {
        versions.append(id, value);
    }

    @Override
    public Optional<SloTargetVersion> findById(SloTargetVersionId id) {
        return versions.findById(id);
    }

    @Override
    public List<SloTargetVersion> findAll() {
        return versions.findAll();
    }

    @Override
    public List<SloTargetVersion> findByCategory(IncidentCategory category) {
        return versions.findByCategory(category);
    }

    @Override
    public synchronized void commit(AuditedMutation<SloTargetVersion> mutation) {
        AuditedMutation<SloTargetVersion> required = Objects.requireNonNull(mutation, "mutation");
        if (auditEvents.stream().anyMatch(event -> event.id().equals(required.auditEvent().id()))) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "audit event already exists");
        }
        versions.append(required.nextState().id(), required.nextState());
        List<AuditEvent> nextAuditEvents = new ArrayList<>(auditEvents);
        nextAuditEvents.add(required.auditEvent());
        auditEvents = List.copyOf(nextAuditEvents);
    }

    public synchronized List<AuditEvent> auditEvents() {
        return auditEvents;
    }
}

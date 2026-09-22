package com.company.incidentdesk.persistence.memory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.persistence.IncidentQuery;
import com.company.incidentdesk.persistence.IncidentRepository;
import com.company.incidentdesk.persistence.IncidentSort;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** In-memory incident repository supporting the shared role-list query scopes. */
public final class InMemoryIncidentRepository implements IncidentRepository {
    private final Map<IncidentId, Incident> incidentsById = new LinkedHashMap<>();

    @Override
    public synchronized void create(Incident incident) {
        Incident requiredIncident = Objects.requireNonNull(incident, "incident");
        if (incidentsById.containsKey(requiredIncident.id())) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "incident already exists");
        }
        incidentsById.put(requiredIncident.id(), requiredIncident);
    }

    @Override
    public synchronized void update(Incident incident) {
        Incident requiredIncident = Objects.requireNonNull(incident, "incident");
        if (!incidentsById.containsKey(requiredIncident.id())) {
            throw new RepositoryException(StorageFailureCode.NOT_FOUND, "incident does not exist");
        }
        incidentsById.put(requiredIncident.id(), requiredIncident);
    }

    @Override
    public synchronized Optional<Incident> findById(IncidentId incidentId) {
        return Optional.ofNullable(incidentsById.get(Objects.requireNonNull(incidentId, "incidentId")));
    }

    @Override
    public synchronized List<Incident> find(IncidentQuery query, IncidentSort sort) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(sort, "sort");
        return incidentsById.values().stream()
                .filter(incident -> matches(query, incident))
                .sorted(sort.comparator())
                .toList();
    }

    private static boolean matches(IncidentQuery query, Incident incident) {
        return switch (query.scope()) {
        case REPORTER_OWNED -> incident.reporterId().equals(query.accountId().orElseThrow());
        case RESPONDER_ELIGIBLE_UNASSIGNED -> incident.status() == IncidentStatus.SUBMITTED
                && incident.assigneeId().isEmpty()
                && query.categories().contains(incident.category());
        case RESPONDER_ASSIGNED -> incident.status() == IncidentStatus.ASSIGNED
                && isAssignedTo(incident, query.accountId().orElseThrow());
        case ADMINISTRATOR_ALL -> true;
        };
    }

    private static boolean isAssignedTo(Incident incident, AccountId responderId) {
        return incident.assigneeId().filter(responderId::equals).isPresent();
    }
}

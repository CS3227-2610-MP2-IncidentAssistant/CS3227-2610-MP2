package com.company.incidentdesk.persistence.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.company.incidentdesk.application.incident.IncidentMutation;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.comment.IncidentComment;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.domain.incident.ReopenExplanation;
import com.company.incidentdesk.persistence.AuditedMutation;
import com.company.incidentdesk.persistence.IncidentQuery;
import com.company.incidentdesk.persistence.IncidentStore;
import com.company.incidentdesk.persistence.IncidentSort;
import com.company.incidentdesk.persistence.AssignmentState;
import com.company.incidentdesk.persistence.IncidentSearchCriteria;
import com.company.incidentdesk.persistence.IncidentSloClassifier;
import com.company.incidentdesk.persistence.IncidentSloState;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** In-memory incident repository supporting the shared role-list query scopes. */
public final class InMemoryIncidentRepository implements IncidentStore {
    private final Consumer<AuditedMutation<IncidentMutation>> commitPreparation;
    private final IncidentSloClassifier sloClassifier;
    private Map<IncidentId, Incident> incidentsById = new LinkedHashMap<>();
    private List<ReopenExplanation> reopenExplanations = List.of();
    private List<IncidentComment> comments = List.of();
    private List<AuditEvent> auditEvents = List.of();

    public InMemoryIncidentRepository() {
        this(ignored -> { }, incident -> IncidentSloState.NOT_APPLICABLE);
    }

    public InMemoryIncidentRepository(
            Consumer<AuditedMutation<IncidentMutation>> commitPreparation) {
        this(commitPreparation, incident -> IncidentSloState.NOT_APPLICABLE);
    }

    public InMemoryIncidentRepository(
            Consumer<AuditedMutation<IncidentMutation>> commitPreparation,
            IncidentSloClassifier sloClassifier) {
        this.commitPreparation = Objects.requireNonNull(commitPreparation, "commitPreparation");
        this.sloClassifier = Objects.requireNonNull(sloClassifier, "sloClassifier");
    }

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

    @Override
    public synchronized List<Incident> find(IncidentQuery query, IncidentSearchCriteria criteria) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(criteria, "criteria");
        return incidentsById.values().stream()
                .filter(incident -> matches(query, incident))
                .filter(incident -> matches(criteria, incident))
                .sorted(criteria.sort().comparator())
                .toList();
    }

    @Override
    public synchronized void commit(AuditedMutation<IncidentMutation> auditedMutation) {
        AuditedMutation<IncidentMutation> requiredMutation = Objects.requireNonNull(
                auditedMutation,
                "auditedMutation");
        rejectDuplicateAuditEvent(requiredMutation.auditEvent());

        Map<IncidentId, Incident> nextIncidents = new LinkedHashMap<>(incidentsById);
        List<ReopenExplanation> nextExplanations = new ArrayList<>(reopenExplanations);
        List<IncidentComment> nextComments = new ArrayList<>(comments);
        List<AuditEvent> nextAuditEvents = new ArrayList<>(auditEvents);
        apply(requiredMutation.nextState(), nextIncidents, nextExplanations, nextComments);
        nextAuditEvents.add(requiredMutation.auditEvent());

        commitPreparation.accept(requiredMutation);
        incidentsById = nextIncidents;
        reopenExplanations = List.copyOf(nextExplanations);
        comments = List.copyOf(nextComments);
        auditEvents = List.copyOf(nextAuditEvents);
    }

    public synchronized List<ReopenExplanation> reopenExplanations() {
        return reopenExplanations;
    }

    public synchronized List<AuditEvent> auditEvents() {
        return auditEvents;
    }

    @Override
    public synchronized List<IncidentComment> findCommentsByIncidentId(IncidentId incidentId) {
        IncidentId requiredId = Objects.requireNonNull(incidentId, "incidentId");
        return comments.stream().filter(comment -> comment.incidentId().equals(requiredId)).toList();
    }

    private void rejectDuplicateAuditEvent(AuditEvent auditEvent) {
        boolean duplicate = auditEvents.stream().anyMatch(existing -> existing.id().equals(auditEvent.id()));
        if (duplicate) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "audit event already exists");
        }
    }

    private static void apply(
            IncidentMutation mutation,
            Map<IncidentId, Incident> incidents,
            List<ReopenExplanation> explanations,
            List<IncidentComment> comments) {
        Incident incident = mutation.incident();
        switch (mutation.type()) {
        case CREATE -> {
            if (incidents.putIfAbsent(incident.id(), incident) != null) {
                throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "incident already exists");
            }
        }
        case UPDATE -> {
            if (incidents.replace(incident.id(), incident) == null) {
                throw new RepositoryException(StorageFailureCode.NOT_FOUND, "incident does not exist");
            }
        }
        }
        mutation.reopenExplanation().ifPresent(explanations::add);
        mutation.comment().ifPresent(comment -> {
            if (comments.stream().anyMatch(existing -> existing.id().equals(comment.id()))) {
                throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "comment already exists");
            }
            comments.add(comment);
        });
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

    private boolean matches(IncidentSearchCriteria criteria, Incident incident) {
        return matchesText(criteria.text(), incident)
                && matchesCategories(criteria, incident)
                && matchesStatuses(criteria, incident)
                && matchesAssignment(criteria.assignmentState(), incident)
                && matchesReporter(criteria, incident)
                && matchesResponder(criteria, incident)
                && matchesCreatedRange(criteria, incident)
                && matchesSloState(criteria, incident);
    }

    private static boolean matchesText(String text, Incident incident) {
        String searchText = text.toLowerCase(Locale.ROOT);
        return searchText.isEmpty()
                || containsIgnoringCase(incident.title(), searchText)
                || containsIgnoringCase(incident.description(), searchText)
                || containsIgnoringCase(incident.id().value().toString(), searchText);
    }

    private static boolean containsIgnoringCase(String value, String normalizedSearchText) {
        return value.toLowerCase(Locale.ROOT).contains(normalizedSearchText);
    }

    private static boolean matchesCategories(IncidentSearchCriteria criteria, Incident incident) {
        return criteria.categories().isEmpty() || criteria.categories().contains(incident.category());
    }

    private static boolean matchesStatuses(IncidentSearchCriteria criteria, Incident incident) {
        return criteria.statuses().isEmpty() || criteria.statuses().contains(incident.status());
    }

    private static boolean matchesAssignment(AssignmentState assignmentState, Incident incident) {
        return switch (assignmentState) {
        case ANY -> true;
        case ASSIGNED -> incident.assigneeId().isPresent();
        case UNASSIGNED -> incident.assigneeId().isEmpty();
        };
    }

    private static boolean matchesReporter(IncidentSearchCriteria criteria, Incident incident) {
        // Anonymous incidents never participate in reporter-identity filtering.
        return criteria.reporterId().isEmpty()
                || (!incident.anonymous() && criteria.reporterId().orElseThrow().equals(incident.reporterId()));
    }

    private static boolean matchesResponder(IncidentSearchCriteria criteria, Incident incident) {
        return criteria.responderId().isEmpty()
                || incident.assigneeId().filter(criteria.responderId().orElseThrow()::equals).isPresent();
    }

    private static boolean matchesCreatedRange(IncidentSearchCriteria criteria, Incident incident) {
        boolean afterStart = criteria.createdFrom()
                .map(from -> !incident.createdAt().isBefore(from))
                .orElse(true);
        boolean beforeEnd = criteria.createdThrough()
                .map(through -> !incident.createdAt().isAfter(through))
                .orElse(true);
        return afterStart && beforeEnd;
    }

    private boolean matchesSloState(IncidentSearchCriteria criteria, Incident incident) {
        return criteria.sloStates().isEmpty()
                || criteria.sloStates().contains(sloClassifier.classify(incident));
    }

    private static boolean isAssignedTo(Incident incident, AccountId responderId) {
        return incident.assigneeId().filter(responderId::equals).isPresent();
    }
}

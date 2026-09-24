package com.company.incidentdesk.persistence.file;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.application.incident.IncidentMutation;
import com.company.incidentdesk.application.account.AccountRegistration;
import com.company.incidentdesk.application.account.AccountRegistrationStore;
import com.company.incidentdesk.application.account.PasswordCredential;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.audit.AuditEvent;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.comment.IncidentComment;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.domain.slo.SloTargetVersionId;
import com.company.incidentdesk.persistence.AccountRepository;
import com.company.incidentdesk.persistence.AssignmentState;
import com.company.incidentdesk.persistence.AuditQuery;
import com.company.incidentdesk.persistence.AuditRepository;
import com.company.incidentdesk.persistence.AuditSortDirection;
import com.company.incidentdesk.persistence.AuditedMutation;
import com.company.incidentdesk.persistence.IncidentQuery;
import com.company.incidentdesk.persistence.IncidentSearchCriteria;
import com.company.incidentdesk.persistence.IncidentSloClassifier;
import com.company.incidentdesk.persistence.IncidentSloState;
import com.company.incidentdesk.persistence.IncidentSort;
import com.company.incidentdesk.persistence.IncidentStore;
import com.company.incidentdesk.persistence.RepositoryException;
import com.company.incidentdesk.persistence.SloConfigurationStore;
import com.company.incidentdesk.persistence.StorageFailureCode;

/** Durable aggregate repository for accounts, incidents, comments, audits, and SLO configuration. */
public final class LocalApplicationStore
        implements AccountRepository, AccountRegistrationStore, IncidentStore, AuditRepository, AutoCloseable {
    public static final String STATE_FILE_NAME = "incident-desk.dat";

    private final ApplicationProcessLock processLock;
    private final RecoverySafeFile<LocalApplicationState> file;
    private final IncidentSloClassifier sloClassifier;
    private final SloConfigurationStore sloConfigurationStore = new SloConfigurationStoreFacet();
    private LocalApplicationState state;

    public LocalApplicationStore(Path dataDirectory) {
        this(dataDirectory, incident -> IncidentSloState.NOT_APPLICABLE);
    }

    public LocalApplicationStore(Path dataDirectory, IncidentSloClassifier sloClassifier) {
        Path requiredDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.sloClassifier = Objects.requireNonNull(sloClassifier, "sloClassifier");
        processLock = ApplicationProcessLock.acquire(requiredDirectory);
        file = new RecoverySafeFile<>(requiredDirectory, STATE_FILE_NAME, new LocalApplicationStateCodec());
        try {
            state = file.load().orElseGet(LocalApplicationState::empty);
        } catch (RuntimeException exception) {
            processLock.close();
            throw exception;
        }
    }

    public static LocalApplicationStore openDefault() {
        return new LocalApplicationStore(ApplicationDataDirectory.resolve());
    }

    public static void recoverLastKnownGoodBackup(Path dataDirectory) {
        Path requiredDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        try (ApplicationProcessLock ignored = ApplicationProcessLock.acquire(requiredDirectory)) {
            RecoverySafeFile<LocalApplicationState> recoveryFile = new RecoverySafeFile<>(
                    requiredDirectory, STATE_FILE_NAME, new LocalApplicationStateCodec());
            recoveryFile.restoreBackup();
        }
    }

    @Override
    public synchronized void create(Account account) {
        Account required = Objects.requireNonNull(account, "account");
        Map<AccountId, Account> accounts = new LinkedHashMap<>(state.accounts());
        if (accounts.putIfAbsent(required.id(), required) != null
                || accounts.values().stream().anyMatch(existing -> existing != required
                        && existing.loginName().equals(required.loginName()))) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "account already exists");
        }
        persist(new LocalApplicationState(
                accounts, state.credentials(), state.incidents(), state.comments(), state.auditEvents(), state.sloTargetVersions()));
    }

    @Override
    public synchronized void update(Account account) {
        Account required = Objects.requireNonNull(account, "account");
        Map<AccountId, Account> accounts = new LinkedHashMap<>(state.accounts());
        if (accounts.replace(required.id(), required) == null) {
            throw new RepositoryException(StorageFailureCode.NOT_FOUND, "account does not exist");
        }
        boolean duplicateLogin = accounts.values().stream()
                .anyMatch(existing -> !existing.id().equals(required.id())
                        && existing.loginName().equals(required.loginName()));
        if (duplicateLogin) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "account login already exists");
        }
        persist(new LocalApplicationState(
                accounts, state.credentials(), state.incidents(), state.comments(), state.auditEvents(), state.sloTargetVersions()));
    }

    @Override
    public synchronized void register(AccountRegistration registration) {
        AccountRegistration required = Objects.requireNonNull(registration, "registration");
        if (state.accounts().containsKey(required.account().id()) || state.accounts().values().stream()
                .anyMatch(account -> account.loginName().equals(required.account().loginName()))) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "account already exists");
        }
        rejectDuplicateAudit(required.auditEvent());
        Map<AccountId, Account> accounts = new LinkedHashMap<>(state.accounts());
        accounts.put(required.account().id(), required.account());
        Map<AccountId, PasswordCredential> credentials = new LinkedHashMap<>(state.credentials());
        credentials.put(required.account().id(), required.credential());
        List<AuditEvent> audits = new ArrayList<>(state.auditEvents());
        audits.add(required.auditEvent());
        persist(new LocalApplicationState(accounts, credentials, state.incidents(), state.comments(), audits,
                state.sloTargetVersions()));
    }

    @Override
    public synchronized Optional<PasswordCredential> findCredential(AccountId accountId) {
        return Optional.ofNullable(state.credentials().get(Objects.requireNonNull(accountId, "accountId")));
    }

    @Override
    public synchronized Optional<Account> findById(AccountId accountId) {
        return Optional.ofNullable(state.accounts().get(Objects.requireNonNull(accountId, "accountId")));
    }

    @Override
    public synchronized Optional<Account> findByLoginName(String loginName) {
        Objects.requireNonNull(loginName, "loginName");
        return state.accounts().values().stream().filter(account -> account.loginName().equals(loginName)).findFirst();
    }

    @Override
    public synchronized List<Account> findAll() {
        return state.accounts().values().stream()
                .sorted(Comparator.comparing(account -> account.id().value())).toList();
    }

    @Override
    public synchronized void create(Incident incident) {
        replaceIncident(Objects.requireNonNull(incident, "incident"), false);
    }

    @Override
    public synchronized void update(Incident incident) {
        replaceIncident(Objects.requireNonNull(incident, "incident"), true);
    }

    @Override
    public synchronized Optional<Incident> findById(IncidentId incidentId) {
        return Optional.ofNullable(state.incidents().get(Objects.requireNonNull(incidentId, "incidentId")));
    }

    @Override
    public synchronized List<Incident> find(IncidentQuery query, IncidentSort sort) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(sort, "sort");
        return state.incidents().values().stream().filter(incident -> matches(query, incident))
                .sorted(sort.comparator()).toList();
    }

    @Override
    public synchronized List<Incident> find(IncidentQuery query, IncidentSearchCriteria criteria) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(criteria, "criteria");
        return state.incidents().values().stream().filter(incident -> matches(query, incident))
                .filter(incident -> matches(criteria, incident)).sorted(criteria.sort().comparator()).toList();
    }

    @Override
    public synchronized void commit(AuditedMutation<IncidentMutation> auditedMutation) {
        AuditedMutation<IncidentMutation> required = Objects.requireNonNull(auditedMutation, "auditedMutation");
        rejectDuplicateAudit(required.auditEvent());
        Map<IncidentId, Incident> incidents = new LinkedHashMap<>(state.incidents());
        List<IncidentComment> comments = new ArrayList<>(state.comments());
        apply(required.nextState(), incidents, comments);
        List<AuditEvent> audits = new ArrayList<>(state.auditEvents());
        audits.add(required.auditEvent());
        persist(new LocalApplicationState(state.accounts(), state.credentials(), incidents, comments, audits, state.sloTargetVersions()));
    }

    @Override
    public synchronized List<IncidentComment> findCommentsByIncidentId(IncidentId incidentId) {
        IncidentId required = Objects.requireNonNull(incidentId, "incidentId");
        return state.comments().stream().filter(comment -> comment.incidentId().equals(required)).toList();
    }

    @Override
    public synchronized void append(AuditEvent event) {
        AuditEvent required = Objects.requireNonNull(event, "event");
        rejectDuplicateAudit(required);
        List<AuditEvent> audits = new ArrayList<>(state.auditEvents());
        audits.add(required);
        persist(new LocalApplicationState(
                state.accounts(), state.credentials(), state.incidents(), state.comments(), audits, state.sloTargetVersions()));
    }

    /** Returns the SLO configuration facet of this aggregate store. */
    public SloConfigurationStore sloConfigurationStore() {
        return sloConfigurationStore;
    }

    @Override
    public synchronized Optional<AuditEvent> findById(AuditEventId eventId) {
        AuditEventId required = Objects.requireNonNull(eventId, "eventId");
        return state.auditEvents().stream().filter(event -> event.id().equals(required)).findFirst();
    }

    @Override
    public synchronized List<AuditEvent> find(AuditQuery query, AuditSortDirection direction) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(direction, "direction");
        Comparator<AuditEvent> order = AuditEvent.CHRONOLOGICAL_ORDER;
        if (direction == AuditSortDirection.NEWEST_FIRST) {
            order = order.reversed();
        }
        return state.auditEvents().stream().filter(event -> matches(query, event)).sorted(order).toList();
    }

    public synchronized void restoreLastKnownGoodBackup() {
        state = file.restoreBackup();
    }

    @Override
    public void close() {
        processLock.close();
    }

    private void replaceIncident(Incident incident, boolean update) {
        Map<IncidentId, Incident> incidents = new LinkedHashMap<>(state.incidents());
        Incident previous = update ? incidents.replace(incident.id(), incident) : incidents.putIfAbsent(incident.id(), incident);
        if (update && previous == null) {
            throw new RepositoryException(StorageFailureCode.NOT_FOUND, "incident does not exist");
        }
        if (!update && previous != null) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "incident already exists");
        }
        persist(new LocalApplicationState(
                state.accounts(), state.credentials(), incidents, state.comments(), state.auditEvents(), state.sloTargetVersions()));
    }

    private void persist(LocalApplicationState nextState) {
        file.save(nextState);
        state = nextState;
    }

    private void rejectDuplicateAudit(AuditEvent event) {
        if (state.auditEvents().stream().anyMatch(existing -> existing.id().equals(event.id()))) {
            throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "audit event already exists");
        }
    }

    private static void apply(IncidentMutation mutation, Map<IncidentId, Incident> incidents,
            List<IncidentComment> comments) {
        Incident incident = mutation.incident();
        if (mutation.type() == IncidentMutation.Type.CREATE) {
            if (incidents.putIfAbsent(incident.id(), incident) != null) {
                throw new RepositoryException(StorageFailureCode.ALREADY_EXISTS, "incident already exists");
            }
        } else if (incidents.replace(incident.id(), incident) == null) {
            throw new RepositoryException(StorageFailureCode.NOT_FOUND, "incident does not exist");
        }
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
                && incident.assigneeId().isEmpty() && query.categories().contains(incident.category());
        case RESPONDER_ASSIGNED -> incident.status() == IncidentStatus.ASSIGNED
                && incident.assigneeId().filter(query.accountId().orElseThrow()::equals).isPresent();
        case ADMINISTRATOR_ALL -> true;
        };
    }

    private boolean matches(IncidentSearchCriteria criteria, Incident incident) {
        String text = criteria.text().toLowerCase(Locale.ROOT);
        boolean textMatches = text.isEmpty() || incident.title().toLowerCase(Locale.ROOT).contains(text)
                || incident.description().toLowerCase(Locale.ROOT).contains(text)
                || incident.id().value().toString().contains(text);
        boolean rangeMatches = criteria.createdFrom().map(start -> !incident.createdAt().isBefore(start)).orElse(true)
                && criteria.createdThrough().map(end -> !incident.createdAt().isAfter(end)).orElse(true);
        return textMatches
                && (criteria.categories().isEmpty() || criteria.categories().contains(incident.category()))
                && (criteria.statuses().isEmpty() || criteria.statuses().contains(incident.status()))
                && matchesAssignment(criteria.assignmentState(), incident)
                && criteria.reporterId().map(id -> !incident.anonymous() && id.equals(incident.reporterId())).orElse(true)
                && criteria.responderId().map(id -> incident.assigneeId().filter(id::equals).isPresent()).orElse(true)
                && rangeMatches
                && (criteria.sloStates().isEmpty() || criteria.sloStates().contains(sloClassifier.classify(incident)));
    }

    private static boolean matchesAssignment(AssignmentState state, Incident incident) {
        return switch (state) {
        case ANY -> true;
        case ASSIGNED -> incident.assigneeId().isPresent();
        case UNASSIGNED -> incident.assigneeId().isEmpty();
        };
    }

    private static boolean matches(AuditQuery query, AuditEvent event) {
        Instant occurredAt = event.occurredAt();
        return query.fromInclusive().map(start -> !occurredAt.isBefore(start)).orElse(true)
                && query.toExclusive().map(end -> occurredAt.isBefore(end)).orElse(true)
                && query.actorId().map(id -> id.equals(event.actor().accountId())).orElse(true)
                && (query.actions().isEmpty() || query.actions().contains(event.action()))
                && query.target().map(event.target()::equals).orElse(true);
    }

    /** SLO configuration facet sharing this aggregate's canonical state and safe-write protocol. */
    private final class SloConfigurationStoreFacet implements SloConfigurationStore {
        @Override
        public void append(SloTargetVersionId id, SloTargetVersion value) {
            synchronized (LocalApplicationStore.this) {
                SloTargetVersionId requiredId = Objects.requireNonNull(id, "id");
                SloTargetVersion requiredValue = Objects.requireNonNull(value, "value");
                if (!requiredId.equals(requiredValue.id())) {
                    throw new IllegalArgumentException("id must match the SLO target version identifier");
                }
                Map<SloTargetVersionId, SloTargetVersion> sloTargetVersions =
                        new LinkedHashMap<>(state.sloTargetVersions());
                if (sloTargetVersions.putIfAbsent(requiredId, requiredValue) != null) {
                    throw new RepositoryException(
                            StorageFailureCode.ALREADY_EXISTS, "SLO target version already exists");
                }
                persist(new LocalApplicationState(
                        state.accounts(), state.credentials(), state.incidents(), state.comments(), state.auditEvents(),
                        sloTargetVersions));
            }
        }

        @Override
        public Optional<SloTargetVersion> findById(SloTargetVersionId id) {
            synchronized (LocalApplicationStore.this) {
                return Optional.ofNullable(state.sloTargetVersions().get(Objects.requireNonNull(id, "id")));
            }
        }

        @Override
        public List<SloTargetVersion> findAll() {
            synchronized (LocalApplicationStore.this) {
                return state.sloTargetVersions().values().stream()
                        .sorted(Comparator.comparing(version -> version.id().value())).toList();
            }
        }

        @Override
        public List<SloTargetVersion> findByCategory(IncidentCategory category) {
            IncidentCategory requiredCategory = Objects.requireNonNull(category, "category");
            return findAll().stream().filter(version -> version.category() == requiredCategory).toList();
        }

        @Override
        public void commit(AuditedMutation<SloTargetVersion> auditedMutation) {
            synchronized (LocalApplicationStore.this) {
                AuditedMutation<SloTargetVersion> required = Objects.requireNonNull(
                        auditedMutation, "auditedMutation");
                rejectDuplicateAudit(required.auditEvent());
                Map<SloTargetVersionId, SloTargetVersion> sloTargetVersions =
                        new LinkedHashMap<>(state.sloTargetVersions());
                SloTargetVersion version = required.nextState();
                if (sloTargetVersions.putIfAbsent(version.id(), version) != null) {
                    throw new RepositoryException(
                            StorageFailureCode.ALREADY_EXISTS, "SLO target version already exists");
                }
                List<AuditEvent> audits = new ArrayList<>(state.auditEvents());
                audits.add(required.auditEvent());
                persist(new LocalApplicationState(
                        state.accounts(), state.credentials(), state.incidents(), state.comments(), audits, sloTargetVersions));
            }
        }
    }
}

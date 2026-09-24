package com.company.incidentdesk.ui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.slo.SloConfigurationGateway;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.slo.SloTarget;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.domain.slo.SloTargetVersionId;

class AdminSloPresenterTest {
    @Test
    void startsLoadingAndShowsEveryConfiguredCategory() {
        FakeGateway gateway = new FakeGateway();
        for (IncidentCategory category : IncidentCategory.values()) {
            gateway.add(version(category, gateway.nextId++, 30));
        }
        AdminSloPresenter presenter = new AdminSloPresenter(gateway);

        assertEquals(AdminSloPresenter.State.LOADING, presenter.state());
        presenter.load();

        assertEquals(AdminSloPresenter.State.READY, presenter.state());
        for (IncidentCategory category : IncidentCategory.values()) {
            assertTrue(presenter.current(category).isPresent());
        }
    }

    @Test
    void supportsInitialConfigurationAndSubsequentEditsWithoutReplacingHistory() {
        FakeGateway gateway = new FakeGateway();
        AdminSloPresenter presenter = new AdminSloPresenter(gateway);
        presenter.load();

        assertTrue(presenter.current(IncidentCategory.IT).isEmpty());
        presenter.save("30", "240", "10");
        presenter.save("15", "120", "20");

        assertEquals(AdminSloPresenter.State.SAVED, presenter.state());
        assertEquals(Duration.ofMinutes(15), presenter.current(IncidentCategory.IT)
                .orElseThrow().target().timeToClaimTarget());
        assertEquals(2, presenter.history().size());
        assertEquals(Duration.ofMinutes(30), presenter.history().getFirst().target().timeToClaimTarget());
    }

    @Test
    void unchangedTargetsCannotCreateDuplicateVersions() {
        FakeGateway gateway = new FakeGateway();
        SloTargetVersion original = version(IncidentCategory.IT, gateway.nextId++, 30);
        gateway.add(original);
        AdminSloPresenter presenter = new AdminSloPresenter(gateway);
        presenter.load();

        assertTrue(!presenter.hasChanges("30", "240", "10"));
        presenter.save("30", "240", "10.0");

        assertEquals(AdminSloPresenter.State.READY, presenter.state());
        assertEquals(0, gateway.configureCalls);
        assertEquals(List.of(original), presenter.history());
    }

    @Test
    void blankInitialFormCannotCreateAConfigurationVersion() {
        FakeGateway gateway = new FakeGateway();
        AdminSloPresenter presenter = new AdminSloPresenter(gateway);
        presenter.load();

        assertTrue(!presenter.hasChanges("", " ", ""));
        presenter.save("", " ", "");

        assertEquals(AdminSloPresenter.State.READY, presenter.state());
        assertEquals(0, gateway.configureCalls);
    }

    @Test
    void selectingEachCategoryLoadsItsReadOnlyHistory() {
        FakeGateway gateway = new FakeGateway();
        for (IncidentCategory category : IncidentCategory.values()) {
            gateway.add(version(category, gateway.nextId++, 10 + category.ordinal()));
        }
        AdminSloPresenter presenter = new AdminSloPresenter(gateway);
        presenter.load();

        for (IncidentCategory category : IncidentCategory.values()) {
            presenter.selectCategory(category);
            assertEquals(category, presenter.selectedCategory());
            assertEquals(List.of(gateway.versions.get(category).getFirst()), presenter.history());
        }
    }

    @Test
    void rejectsInvalidInputWithoutCallingPersistence() {
        FakeGateway gateway = new FakeGateway();
        AdminSloPresenter presenter = new AdminSloPresenter(gateway);
        presenter.load();

        presenter.save("-1", "abc", "101");

        assertEquals(AdminSloPresenter.State.VALIDATION_ERROR, presenter.state());
        assertEquals(0, gateway.configureCalls);
        assertTrue(presenter.current(IncidentCategory.IT).isEmpty());
    }

    @Test
    void mapsDeniedActorsToUnavailableWithoutPrivilegedData() {
        FakeGateway gateway = new FakeGateway();
        gateway.failure = ApplicationErrorCode.RESOURCE_UNAVAILABLE;
        AdminSloPresenter presenter = new AdminSloPresenter(gateway);

        presenter.load();

        assertEquals(AdminSloPresenter.State.UNAVAILABLE, presenter.state());
        assertTrue(presenter.history().isEmpty());
        for (IncidentCategory category : IncidentCategory.values()) {
            assertTrue(presenter.current(category).isEmpty());
        }
    }

    @Test
    void reportsStorageFailureAndKeepsThePreviouslyDisplayedVersion() {
        FakeGateway gateway = new FakeGateway();
        SloTargetVersion original = version(IncidentCategory.IT, gateway.nextId++, 30);
        gateway.add(original);
        AdminSloPresenter presenter = new AdminSloPresenter(gateway);
        presenter.load();
        gateway.failure = ApplicationErrorCode.PERSISTENCE_FAILURE;

        presenter.save("15", "120", "20");

        assertEquals(AdminSloPresenter.State.STORAGE_ERROR, presenter.state());
        assertEquals(original, presenter.current(IncidentCategory.IT).orElseThrow());
        assertEquals(List.of(original), gateway.versions.get(IncidentCategory.IT));
    }

    private static SloTargetVersion version(IncidentCategory category, long id, long claimMinutes) {
        return new SloTargetVersion(
                new SloTargetVersionId(new UUID(0, id)), category,
                new SloTarget(Duration.ofMinutes(claimMinutes), Duration.ofHours(4), 0.1),
                Instant.parse("2026-09-24T00:00:00Z").plusSeconds(id),
                new AccountId(new UUID(1, 1)));
    }

    private static final class FakeGateway implements SloConfigurationGateway {
        private final Map<IncidentCategory, List<SloTargetVersion>> versions =
                new EnumMap<>(IncidentCategory.class);
        private ApplicationErrorCode failure;
        private long nextId = 1;
        private int configureCalls;

        @Override
        public ApplicationResult<SloTargetVersion> configure(
                IncidentCategory category,
                Duration timeToClaimTarget,
                Duration timeInProgressTarget,
                double reopenRateTarget) {
            configureCalls++;
            if (failure != null) {
                return ApplicationResult.failure(ApplicationError.of(failure));
            }
            SloTargetVersion configured = new SloTargetVersion(
                    new SloTargetVersionId(new UUID(0, nextId)), category,
                    new SloTarget(timeToClaimTarget, timeInProgressTarget, reopenRateTarget),
                    Instant.parse("2026-09-24T00:00:00Z").plusSeconds(nextId++),
                    new AccountId(new UUID(1, 1)));
            add(configured);
            return ApplicationResult.success(configured);
        }

        @Override
        public ApplicationResult<List<SloTargetVersion>> currentTargets() {
            if (failure != null) {
                return ApplicationResult.failure(ApplicationError.of(failure));
            }
            return ApplicationResult.success(versions.values().stream()
                    .filter(history -> !history.isEmpty())
                    .map(List::getLast)
                    .toList());
        }

        @Override
        public ApplicationResult<List<SloTargetVersion>> history(IncidentCategory category) {
            if (failure != null) {
                return ApplicationResult.failure(ApplicationError.of(failure));
            }
            return ApplicationResult.success(List.copyOf(versions.getOrDefault(category, List.of())));
        }

        private void add(SloTargetVersion version) {
            versions.computeIfAbsent(version.category(), ignored -> new ArrayList<>()).add(version);
        }
    }
}

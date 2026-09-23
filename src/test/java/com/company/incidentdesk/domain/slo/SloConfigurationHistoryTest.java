package com.company.incidentdesk.domain.slo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.IncidentCategory;

/** Verifies configuration changes apply prospectively without rewriting historical compliance. */
class SloConfigurationHistoryTest {
    private static final Instant FIRST_EFFECTIVE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant SECOND_EFFECTIVE = Instant.parse("2026-02-01T00:00:00Z");

    @Test
    void emptyHistoryHasNoApplicableVersion() {
        SloConfigurationHistory history = new SloConfigurationHistory(List.of());
        assertEquals(Optional.empty(), history.targetFor(IncidentCategory.IT, Instant.now()));
        assertEquals(Optional.empty(), history.latest(IncidentCategory.IT));
    }

    @Test
    void aLaterConfigurationEditDoesNotRewriteAnEarlierCyclesApplicableTarget() {
        SloTargetVersion original = version(1, IncidentCategory.IT, FIRST_EFFECTIVE, Duration.ofMinutes(30));
        SloTargetVersion revised = version(2, IncidentCategory.IT, SECOND_EFFECTIVE, Duration.ofMinutes(15));
        SloConfigurationHistory history = new SloConfigurationHistory(List.of(original, revised));

        Instant earlyCycleTime = FIRST_EFFECTIVE.plusSeconds(1);
        Instant lateCycleTime = SECOND_EFFECTIVE.plusSeconds(1);

        assertEquals(original, history.targetFor(IncidentCategory.IT, earlyCycleTime).orElseThrow());
        assertEquals(revised, history.targetFor(IncidentCategory.IT, lateCycleTime).orElseThrow());
        assertEquals(revised, history.latest(IncidentCategory.IT).orElseThrow());
    }

    @Test
    void noVersionAppliesBeforeItsEffectiveTime() {
        SloTargetVersion version = version(3, IncidentCategory.FACILITIES, SECOND_EFFECTIVE, Duration.ofMinutes(30));
        SloConfigurationHistory history = new SloConfigurationHistory(List.of(version));

        assertTrue(history.targetFor(IncidentCategory.FACILITIES, FIRST_EFFECTIVE).isEmpty());
    }

    @Test
    void categoriesAreConfiguredIndependently() {
        SloTargetVersion itVersion = version(4, IncidentCategory.IT, FIRST_EFFECTIVE, Duration.ofMinutes(30));
        SloConfigurationHistory history = new SloConfigurationHistory(List.of(itVersion));

        assertEquals(Optional.empty(), history.targetFor(IncidentCategory.HUMAN_RELATIONS, FIRST_EFFECTIVE));
    }

    private static SloTargetVersion version(long id, IncidentCategory category, Instant effectiveFrom, Duration target) {
        return new SloTargetVersion(
                new SloTargetVersionId(new UUID(0, id)),
                category,
                new SloTarget(target, Duration.ofHours(4), 0.1),
                effectiveFrom,
                new AccountId(new UUID(0, 100)));
    }
}

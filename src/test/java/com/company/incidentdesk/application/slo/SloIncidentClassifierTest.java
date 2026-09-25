package com.company.incidentdesk.application.slo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.domain.slo.SloTarget;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.domain.slo.SloTargetVersionId;
import com.company.incidentdesk.persistence.IncidentSloState;
import com.company.incidentdesk.persistence.memory.InMemorySloConfigurationStore;

class SloIncidentClassifierTest {
    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-25T00:00:00Z");

    @Test
    void usesTheSamePersistedTargetForFilteringAndRowPresentation() {
        InMemorySloConfigurationStore configurations = new InMemorySloConfigurationStore();
        SloTargetVersionId versionId = new SloTargetVersionId(new UUID(0, 1));
        configurations.append(versionId, new SloTargetVersion(
                versionId, IncidentCategory.IT,
                new SloTarget(Duration.ofMinutes(30), Duration.ofHours(4), 0.1),
                SUBMITTED_AT, new AccountId(new UUID(0, 2))));
        SloIncidentClassifier classifier = new SloIncidentClassifier(configurations,
                Clock.fixed(SUBMITTED_AT.plus(Duration.ofMinutes(31)), ZoneOffset.UTC));
        var incident = new IncidentLifecycle(Clock.fixed(SUBMITTED_AT, ZoneOffset.UTC)).submit(
                new IncidentId(new UUID(0, 3)), new AccountId(new UUID(0, 4)),
                "Network outage", "Description", IncidentCategory.IT, false);

        assertEquals(IncidentSloState.OVERDUE, classifier.classify(incident));
        assertTrue(classifier.summarize(incident).overdue());
        assertTrue(classifier.summarize(incident).label().startsWith("Overdue · Time to claim"));
    }
}

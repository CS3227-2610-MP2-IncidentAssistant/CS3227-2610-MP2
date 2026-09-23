package com.company.incidentdesk.persistence.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.slo.SloTarget;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.domain.slo.SloTargetVersionId;

/** Verifies SLO configuration encoding is round-trippable and validates referenced accounts. */
class LocalApplicationStateCodecTest {
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final AccountId ADMIN_ID = new AccountId(new UUID(0, 1));

    @Test
    void roundTripsSloTargetVersionsAndRejectsAMissingChangedByAccount() throws IOException {
        LocalApplicationStateCodec codec = new LocalApplicationStateCodec();
        Account admin = new Account(ADMIN_ID, "admin", Role.ADMINISTRATOR, AccountStatus.ENABLED, ResponderAccess.NONE);
        SloTargetVersion version = new SloTargetVersion(
                new SloTargetVersionId(new UUID(0, 2)), IncidentCategory.IT,
                new SloTarget(Duration.ofMinutes(30), Duration.ofHours(4), 0.1), CREATED, ADMIN_ID);
        Map<SloTargetVersionId, SloTargetVersion> versions = new LinkedHashMap<>();
        versions.put(version.id(), version);
        LocalApplicationState withAccount = new LocalApplicationState(
                Map.of(ADMIN_ID, admin), Map.of(), java.util.List.of(), java.util.List.of(), versions);

        LocalApplicationState decoded = codec.decode(codec.encode(withAccount));
        assertEquals(versions, decoded.sloTargetVersions());

        LocalApplicationState withoutAccount = new LocalApplicationState(
                Map.of(), Map.of(), java.util.List.of(), java.util.List.of(), versions);
        assertThrows(IOException.class, () -> codec.decode(codec.encode(withoutAccount)));
    }

    @Test
    void emptyStateRoundTripsWithNoSloTargetVersions() throws IOException {
        LocalApplicationStateCodec codec = new LocalApplicationStateCodec();
        LocalApplicationState decoded = codec.decode(codec.encode(LocalApplicationState.empty()));
        assertEquals(Map.of(), decoded.sloTargetVersions());
    }
}

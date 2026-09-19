package com.company.incidentdesk.domain.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;

/** Tests resolution information and remark validation. */
class ResolutionTest {
    private static final Instant RESOLVED_AT = Instant.parse("2026-09-19T08:04:00Z");
    private static final AccountId ACTOR_ID = new AccountId(
            UUID.fromString("9911cce1-bd5e-446c-bc3a-20aa91c6bebe"));
    private static final AccountId RESPONDER_ID = new AccountId(
            UUID.fromString("ba647247-9b36-4e24-86df-f35913fb1c65"));

    @Test
    void preservesResolutionRemarkWhitespace() {
        Resolution resolution = new Resolution(
                "  Restarted the affected service.\n",
                RESOLVED_AT,
                ACTOR_ID,
                RESPONDER_ID);

        assertEquals("  Restarted the affected service.\n", resolution.remarks());
    }

    @Test
    void rejectsBlankResolutionRemarks() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Resolution(" \n ", RESOLVED_AT, ACTOR_ID, RESPONDER_ID));
    }
}

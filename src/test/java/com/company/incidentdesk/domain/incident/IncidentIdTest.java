package com.company.incidentdesk.domain.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Tests stable incident identifier construction. */
class IncidentIdTest {
    @Test
    void rejectsNullValue() {
        assertThrows(NullPointerException.class, () -> new IncidentId(null));
    }

    @Test
    void retainsStableIdentifierValue() {
        UUID value = UUID.fromString("18986f55-e47a-4e9a-9e25-b96caa79d225");

        assertEquals(value, new IncidentId(value).value());
    }
}

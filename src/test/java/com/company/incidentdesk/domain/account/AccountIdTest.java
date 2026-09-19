package com.company.incidentdesk.domain.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Tests stable account identifier construction. */
class AccountIdTest {
    @Test
    void rejectsNullValue() {
        assertThrows(NullPointerException.class, () -> new AccountId(null));
    }

    @Test
    void retainsStableIdentifierValue() {
        UUID value = UUID.fromString("91048d20-cf5b-4fa0-9fb1-e7e967289557");

        assertEquals(value, new AccountId(value).value());
    }
}

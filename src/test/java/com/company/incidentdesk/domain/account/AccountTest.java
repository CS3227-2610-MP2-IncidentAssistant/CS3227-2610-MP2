package com.company.incidentdesk.domain.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.incident.IncidentCategory;

/** Tests account identity and responder-access invariants. */
class AccountTest {
    private static final AccountId ACCOUNT_ID = new AccountId(
            UUID.fromString("a9f9e0f2-2de7-4ba8-a353-12362320f84d"));

    @Test
    void preservesCaseSensitiveLoginName() {
        Account account = new Account(
                ACCOUNT_ID,
                "CaseSensitiveName",
                Role.REPORTER,
                AccountStatus.ENABLED,
                ResponderAccess.NONE);

        assertEquals("CaseSensitiveName", account.loginName());
    }

    @Test
    void rejectsBlankLoginName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Account(
                        ACCOUNT_ID,
                        "   ",
                        Role.REPORTER,
                        AccountStatus.ENABLED,
                        ResponderAccess.NONE));
    }

    @Test
    void rejectsCategoryAccessForNonResponder() {
        ResponderAccess access = ResponderAccess.to(Set.of(IncidentCategory.IT));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Account(
                        ACCOUNT_ID,
                        "reporter",
                        Role.REPORTER,
                        AccountStatus.ENABLED,
                        access));
    }

    @Test
    void responderAccessIsImmutableAndChecksAssignedCategories() {
        Set<IncidentCategory> source = new HashSet<>();
        source.add(IncidentCategory.IT);
        ResponderAccess access = ResponderAccess.to(source);

        source.add(IncidentCategory.FACILITIES);

        assertTrue(access.permits(IncidentCategory.IT));
        assertFalse(access.permits(IncidentCategory.FACILITIES));
        assertThrows(
                UnsupportedOperationException.class,
                () -> access.categories().add(IncidentCategory.FACILITIES));
    }

    @Test
    void exposesEnabledState() {
        Account enabled = new Account(
                ACCOUNT_ID,
                "enabled",
                Role.REPORTER,
                AccountStatus.ENABLED,
                ResponderAccess.NONE);
        Account disabled = new Account(
                ACCOUNT_ID,
                "disabled",
                Role.REPORTER,
                AccountStatus.DISABLED,
                ResponderAccess.NONE);

        assertTrue(enabled.isEnabled());
        assertFalse(disabled.isEnabled());
    }
}

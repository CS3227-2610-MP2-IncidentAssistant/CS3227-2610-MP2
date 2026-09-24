package com.company.incidentdesk.ui.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentCategory;

class AuthenticatedShellPreviewTest {
    @Test
    void providesOneEnabledAccountForEveryRole() {
        Map<String, Account> accounts = AuthenticatedShellPreview.previewAccounts().stream()
                .collect(Collectors.toMap(Account::loginName, account -> account));

        assertEquals(Role.ADMINISTRATOR, accounts.get("admin").role());
        assertEquals(Role.REPORTER, accounts.get("reporter").role());
        assertEquals(Role.RESPONDER, accounts.get("responder").role());
        assertEquals(IncidentCategory.values().length,
                accounts.get("responder").responderAccess().categories().size());
    }
}

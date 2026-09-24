package com.company.incidentdesk.application.account;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.persistence.*;
import com.company.incidentdesk.persistence.file.LocalApplicationStore;

class AccountRegistrationServiceTest {
    @TempDir Path directory;

    @Test
    void registersEachRoleWithSaltedHashesAndSupportsAuthentication() throws Exception {
        try (LocalApplicationStore store = new LocalApplicationStore(directory)) {
            AccountRegistrationService service = new AccountRegistrationService(
                    store, Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC));
            assertEquals(RegistrationResult.REGISTERED, service.register("same", "secret".toCharArray(), Role.REPORTER));
            assertEquals(RegistrationResult.REGISTERED, service.register("Same", "secret".toCharArray(), Role.RESPONDER));
            assertEquals(RegistrationResult.REGISTERED, service.register("admin", "secret".toCharArray(), Role.ADMINISTRATOR));
            assertEquals(RegistrationResult.DUPLICATE_LOGIN_NAME,
                    service.register("same", "another".toCharArray(), Role.ADMINISTRATOR));
            var first = store.findByLoginName("same").orElseThrow();
            var second = store.findByLoginName("Same").orElseThrow();
            assertTrue(service.verify(first.id(), "secret".toCharArray()));
            assertFalse(service.verify(first.id(), "wrong".toCharArray()));
            assertFalse(java.util.Arrays.equals(store.findCredential(first.id()).orElseThrow().salt(),
                    store.findCredential(second.id()).orElseThrow().salt()));
            assertEquals(3, store.find(new AuditQuery(java.util.Optional.empty(), java.util.Optional.empty(),
                    java.util.Optional.empty(), java.util.Set.of(AuditAction.ACCOUNT_REGISTERED),
                    java.util.Optional.empty()), AuditSortDirection.OLDEST_FIRST).size());
        }
        String persisted = new String(Files.readAllBytes(directory.resolve(LocalApplicationStore.STATE_FILE_NAME)),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(persisted.contains("secret"));
    }
}

package com.company.incidentdesk.application.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.session.AuthenticationResult;
import com.company.incidentdesk.application.session.InMemorySessionService;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.persistence.AuditQuery;
import com.company.incidentdesk.persistence.AuditSortDirection;
import com.company.incidentdesk.persistence.file.LocalApplicationStore;

class AccountPasswordServiceTest {
    @TempDir Path directory;

    @Test
    void authenticatedAccountChangesPasswordWithAtomicAuditAndNoPlaintext() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC);
        try (LocalApplicationStore store = new LocalApplicationStore(directory)) {
            AccountRegistrationService credentials = new AccountRegistrationService(store, clock);
            assertEquals(RegistrationResult.REGISTERED,
                    credentials.register("reporter", "old-secret".toCharArray(), Role.REPORTER));
            InMemorySessionService sessions = new InMemorySessionService(store, credentials, clock);
            assertEquals(AuthenticationResult.AUTHENTICATED,
                    sessions.login("reporter", "old-secret".toCharArray()));
            AccountPasswordService service = service(sessions, credentials, store, clock);

            assertEquals(PasswordChangeResult.CHANGED, service.changePassword(
                    "old-secret".toCharArray(), "new-secret".toCharArray(), "new-secret".toCharArray()));
            var accountId = sessions.currentAccount().orElseThrow().id();
            assertFalse(credentials.verify(accountId, "old-secret".toCharArray()));
            assertTrue(credentials.verify(accountId, "new-secret".toCharArray()));
            assertEquals(1, store.find(new AuditQuery(Optional.empty(), Optional.empty(), Optional.empty(),
                    Set.of(AuditAction.PASSWORD_CHANGED), Optional.empty()), AuditSortDirection.OLDEST_FIRST).size());
        }

        String stored = new String(Files.readAllBytes(directory.resolve(LocalApplicationStore.STATE_FILE_NAME)),
                StandardCharsets.ISO_8859_1);
        assertFalse(stored.contains("old-secret"));
        assertFalse(stored.contains("new-secret"));
    }

    @Test
    void rejectsWrongCurrentPasswordAndMismatchedConfirmationWithoutMutation() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC);
        try (LocalApplicationStore store = new LocalApplicationStore(directory)) {
            AccountRegistrationService credentials = new AccountRegistrationService(store, clock);
            credentials.register("reporter", "old-secret".toCharArray(), Role.REPORTER);
            InMemorySessionService sessions = new InMemorySessionService(store, credentials, clock);
            sessions.login("reporter", "old-secret".toCharArray());
            AccountPasswordService service = service(sessions, credentials, store, clock);

            assertEquals(PasswordChangeResult.INVALID_CURRENT_PASSWORD, service.changePassword(
                    "wrong".toCharArray(), "new-secret".toCharArray(), "new-secret".toCharArray()));
            assertEquals(PasswordChangeResult.NEW_PASSWORD_MISMATCH, service.changePassword(
                    "old-secret".toCharArray(), "new-secret".toCharArray(), "different".toCharArray()));
            assertTrue(credentials.verify(sessions.currentAccount().orElseThrow().id(), "old-secret".toCharArray()));
        }
    }

    private static AccountPasswordService service(InMemorySessionService sessions,
            AccountRegistrationService credentials, LocalApplicationStore store, Clock clock) {
        return new AccountPasswordService(sessions, credentials, store,
                new AuditEventFactory(clock, () -> new AuditEventId(UUID.randomUUID())));
    }
}

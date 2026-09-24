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
import com.company.incidentdesk.application.authorization.AccountAuthorizationPolicy;
import com.company.incidentdesk.application.session.AuthenticationResult;
import com.company.incidentdesk.application.session.InMemorySessionService;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.persistence.AuditQuery;
import com.company.incidentdesk.persistence.AuditSortDirection;
import com.company.incidentdesk.persistence.file.LocalApplicationStore;

class AccountPasswordResetServiceTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");

    @Test
    void resetIsOneTimeSupersedingAndRequiresReplacement() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        char[] first;
        char[] second;
        try (LocalApplicationStore store = new LocalApplicationStore(directory)) {
            AccountRegistrationService credentials = new AccountRegistrationService(store, clock);
            credentials.register("admin", "admin-password".toCharArray(), Role.ADMINISTRATOR);
            credentials.register("reporter", "original-password".toCharArray(), Role.REPORTER);
            var reporterId = store.findByLoginName("reporter").orElseThrow().id();
            InMemorySessionService sessions = new InMemorySessionService(store, credentials, clock);
            assertEquals(AuthenticationResult.AUTHENTICATED,
                    sessions.login("admin", "admin-password".toCharArray()));
            AccountPasswordResetService resets = resetService(sessions, credentials, store, clock);

            TemporaryPasswordResetResult firstResult = resets.reset(reporterId);
            first = firstResult.takeTemporaryPassword().orElseThrow();
            assertTrue(firstResult.takeTemporaryPassword().isEmpty());
            second = resets.reset(reporterId).takeTemporaryPassword().orElseThrow();

            sessions.logout();
            assertEquals(AuthenticationResult.REJECTED, sessions.login("reporter", first));
            assertEquals(AuthenticationResult.REJECTED,
                    sessions.login("reporter", "original-password".toCharArray()));
            assertEquals(AuthenticationResult.PASSWORD_CHANGE_REQUIRED, sessions.login("reporter", second));
            assertTrue(sessions.requiresPasswordChange());

            AccountPasswordService passwords = new AccountPasswordService(sessions, credentials, store,
                    auditFactory(clock));
            assertEquals(PasswordChangeResult.CHANGED, passwords.changePassword(
                    second, "permanent-password".toCharArray(), "permanent-password".toCharArray()));
            assertFalse(sessions.requiresPasswordChange());
            sessions.logout();
            assertEquals(AuthenticationResult.REJECTED, sessions.login("reporter", second));
            assertEquals(AuthenticationResult.AUTHENTICATED,
                    sessions.login("reporter", "permanent-password".toCharArray()));
            assertEquals(2, audits(store, AuditAction.PASSWORD_RESET_INITIATED));
            assertEquals(1, audits(store, AuditAction.PASSWORD_RESET_COMPLETED));
        }
        String persisted = new String(Files.readAllBytes(directory.resolve(LocalApplicationStore.STATE_FILE_NAME)),
                StandardCharsets.ISO_8859_1);
        assertFalse(persisted.contains(new String(first)));
        assertFalse(persisted.contains(new String(second)));
    }

    @Test
    void expiredTemporaryPasswordIsRejected() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        try (LocalApplicationStore store = new LocalApplicationStore(directory)) {
            AccountRegistrationService credentials = new AccountRegistrationService(store, clock);
            credentials.register("admin", "admin-password".toCharArray(), Role.ADMINISTRATOR);
            credentials.register("reporter", "original-password".toCharArray(), Role.REPORTER);
            InMemorySessionService sessions = new InMemorySessionService(store, credentials, clock);
            sessions.login("admin", "admin-password".toCharArray());
            char[] temporary = resetService(sessions, credentials, store, clock)
                    .reset(store.findByLoginName("reporter").orElseThrow().id())
                    .takeTemporaryPassword().orElseThrow();
            Clock expired = Clock.fixed(NOW.plus(AccountPasswordResetService.TEMPORARY_PASSWORD_LIFETIME),
                    ZoneOffset.UTC);
            AccountRegistrationService expiredCredentials = new AccountRegistrationService(store, expired);
            InMemorySessionService expiredSessions = new InMemorySessionService(store, expiredCredentials, expired);
            assertEquals(AuthenticationResult.REJECTED, expiredSessions.login("reporter", temporary));
        }
    }

    private static AccountPasswordResetService resetService(InMemorySessionService sessions,
            AccountRegistrationService credentials, LocalApplicationStore store, Clock clock) {
        return new AccountPasswordResetService(sessions, new AccountAuthorizationPolicy(sessions), credentials,
                store, auditFactory(clock), clock);
    }

    private static AuditEventFactory auditFactory(Clock clock) {
        return new AuditEventFactory(clock, () -> new AuditEventId(UUID.randomUUID()));
    }

    private static int audits(LocalApplicationStore store, AuditAction action) {
        return store.find(new AuditQuery(Optional.empty(), Optional.empty(), Optional.empty(), Set.of(action),
                Optional.empty()), AuditSortDirection.OLDEST_FIRST).size();
    }
}

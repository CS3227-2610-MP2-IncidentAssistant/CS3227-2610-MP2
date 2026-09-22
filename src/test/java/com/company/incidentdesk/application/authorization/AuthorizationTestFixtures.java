package com.company.incidentdesk.application.authorization;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;

/** Deterministic account, incident, and session fixtures for authorization tests. */
final class AuthorizationTestFixtures {
    static final Instant CREATED_AT = Instant.parse("2026-09-19T08:00:00Z");
    static final Instant SUBMITTED_AT = Instant.parse("2026-09-19T08:01:00Z");
    static final Instant ASSIGNED_AT = Instant.parse("2026-09-19T08:02:00Z");
    static final Instant RESOLVED_AT = Instant.parse("2026-09-19T08:03:00Z");
    static final AccountId REPORTER_ID = accountId("f24a7d0a-7c48-4703-b9d7-a39c933149a1");
    static final AccountId OTHER_REPORTER_ID = accountId("7a759125-09d5-4509-81bc-93d26078aa39");
    static final AccountId RESPONDER_ID = accountId("f2e6c9a8-c1db-4d9d-ad8a-22a342043835");
    static final AccountId OTHER_RESPONDER_ID = accountId("2bf27a40-726d-423c-ad19-bc781426b2be");
    static final AccountId ADMIN_ID = accountId("5181fa2d-1644-44e0-8202-53283902ee27");
    static final IncidentId INCIDENT_ID = new IncidentId(
            UUID.fromString("61e27e29-7a71-405a-a1a0-24b14e4de0fb"));

    private AuthorizationTestFixtures() {
    }

    static Account reporter(AccountId id) {
        return account(id, Role.REPORTER, AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    static Account responder(AccountId id, IncidentCategory... categories) {
        return account(
                id,
                Role.RESPONDER,
                AccountStatus.ENABLED,
                ResponderAccess.to(Set.of(categories)));
    }

    static Account administrator() {
        return account(ADMIN_ID, Role.ADMINISTRATOR, AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    static Account disabledResponder(AccountId id, IncidentCategory category) {
        return account(
                id,
                Role.RESPONDER,
                AccountStatus.DISABLED,
                ResponderAccess.to(Set.of(category)));
    }

    static Incident draft(boolean anonymous) {
        return lifecycleAt(CREATED_AT).saveDraft(
                INCIDENT_ID,
                REPORTER_ID,
                "Incident title",
                "Incident description",
                IncidentCategory.IT,
                anonymous);
    }

    static Incident submitted(boolean anonymous) {
        return lifecycleAt(SUBMITTED_AT).submit(draft(anonymous));
    }

    static Incident assigned(boolean anonymous) {
        return lifecycleAt(ASSIGNED_AT).claim(submitted(anonymous), RESPONDER_ID);
    }

    static Incident resolved(boolean anonymous) {
        return lifecycleAt(RESOLVED_AT).resolve(assigned(anonymous), RESPONDER_ID, "Resolved");
    }

    private static Account account(
            AccountId id,
            Role role,
            AccountStatus status,
            ResponderAccess responderAccess) {
        return new Account(id, role.name().toLowerCase(), role, status, responderAccess);
    }

    private static AccountId accountId(String value) {
        return new AccountId(UUID.fromString(value));
    }

    private static IncidentLifecycle lifecycleAt(Instant instant) {
        return new IncidentLifecycle(Clock.fixed(instant, ZoneOffset.UTC));
    }

    /** Session source whose account can change between policy checks. */
    static final class MutableSessionProvider implements SessionProvider {
        private Optional<Account> account = Optional.empty();

        void signIn(Account currentAccount) {
            account = Optional.of(currentAccount);
        }

        void signOut() {
            account = Optional.empty();
        }

        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return account.map(current -> new AuthenticatedSession(current.id(), CREATED_AT));
        }

        @Override
        public Optional<Account> currentAccount() {
            return account.filter(Account::isEnabled);
        }
    }
}

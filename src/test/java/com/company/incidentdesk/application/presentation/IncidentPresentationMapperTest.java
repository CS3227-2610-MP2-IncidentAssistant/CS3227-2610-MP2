package com.company.incidentdesk.application.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.account.AccountLookup;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
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

/** Tests role-, lifecycle-, privacy-, and display-time behavior of incident presentation mapping. */
class IncidentPresentationMapperTest {
    private static final Instant CREATED = Instant.parse("2026-09-20T16:00:00Z");
    private static final AccountId REPORTER_ID = id("11111111-1111-1111-1111-111111111111");
    private static final AccountId RESPONDER_ID = id("22222222-2222-2222-2222-222222222222");
    private static final AccountId ADMIN_ID = id("33333333-3333-3333-3333-333333333333");
    private static final Account REPORTER = account(REPORTER_ID, "reporter", Role.REPORTER);
    private static final Account RESPONDER = new Account(
            RESPONDER_ID,
            "responder",
            Role.RESPONDER,
            AccountStatus.ENABLED,
            ResponderAccess.to(Set.of(IncidentCategory.IT)));
    private static final Account ADMIN = account(ADMIN_ID, "admin", Role.ADMINISTRATOR);

    @Test
    void anonymousIdentityUsesNeutralLabelForEveryViewerIncludingOwner() {
        Incident anonymous = submitted(true);

        assertEquals(IncidentPresentationMapper.ANONYMOUS_REPORTER_LABEL,
                mapper(REPORTER).toRow(anonymous).reporterLabel());
        assertEquals(IncidentPresentationMapper.ANONYMOUS_REPORTER_LABEL,
                mapper(RESPONDER).toRow(anonymous).reporterLabel());
        assertEquals(IncidentPresentationMapper.ANONYMOUS_REPORTER_LABEL,
                mapper(ADMIN).toRow(anonymous).reporterLabel());
    }

    @Test
    void nonAnonymousIdentityIsShownOnlyToAuthorizedViewers() {
        Incident incident = submitted(false);
        Account otherReporter = account(
                id("44444444-4444-4444-4444-444444444444"), "other", Role.REPORTER);
        Account facilitiesResponder = responder(
                id("55555555-5555-5555-5555-555555555555"), "facilities-responder", IncidentCategory.FACILITIES);

        assertEquals("reporter", mapper(REPORTER).toRow(incident).reporterLabel());
        assertEquals("reporter", mapper(RESPONDER).toRow(incident).reporterLabel());
        assertEquals("reporter", mapper(ADMIN).toRow(incident).reporterLabel());
        assertThrows(SecurityException.class, () -> mapper(otherReporter).toRow(incident));
        assertThrows(SecurityException.class, () -> mapper(facilitiesResponder).toRow(incident));
    }

    @Test
    void assignedIncidentIdentityIsHiddenFromUnassignedResponder() {
        Incident assigned = lifecycleAt(Instant.parse("2026-09-20T16:01:00Z"))
                .claim(submitted(false), RESPONDER_ID);
        Account otherItResponder = responder(
                id("66666666-6666-6666-6666-666666666666"), "other-it-responder", IncidentCategory.IT);

        assertThrows(SecurityException.class, () -> mapper(otherItResponder).toRow(assigned));
    }

    @Test
    void actionAvailabilityTracksPolicyAcrossRolesAndLifecycleStates() {
        Incident draft = draft(false);
        Incident submitted = submitted(false);
        Incident assigned = lifecycleAt(Instant.parse("2026-09-20T16:01:00Z"))
                .claim(submitted, RESPONDER_ID);
        Incident resolved = lifecycleAt(Instant.parse("2026-09-20T16:02:00Z"))
                .resolve(assigned, RESPONDER_ID, "Restored service");

        assertTrue(mapper(REPORTER).toRow(draft).actions().edit());
        assertFalse(mapper(ADMIN).toRow(draft).actions().edit());
        assertTrue(mapper(REPORTER).toRow(submitted).actions().withdraw());
        assertFalse(mapper(ADMIN).toRow(submitted).actions().withdraw());
        assertTrue(mapper(RESPONDER).toRow(submitted).actions().claim());
        assertFalse(mapper(ADMIN).toRow(submitted).actions().claim());
        assertTrue(mapper(RESPONDER).toRow(assigned).actions().resolve());
        assertFalse(mapper(REPORTER).toRow(assigned).actions().resolve());
        assertTrue(mapper(RESPONDER).toRow(assigned).actions().handoff());
        assertFalse(mapper(ADMIN).toRow(assigned).actions().handoff());
        assertTrue(mapper(ADMIN).toRow(assigned).actions().reassign());
        assertFalse(mapper(RESPONDER).toRow(assigned).actions().reassign());
        assertTrue(mapper(REPORTER).toRow(resolved).actions().reopen());
        assertFalse(mapper(ADMIN).toRow(resolved).actions().reopen());
    }

    @Test
    void detailUsesDisplayZoneAndCopiesRelatedCollections() {
        Incident assigned = lifecycleAt(Instant.parse("2026-09-20T16:30:00Z"))
                .claim(submitted(false), RESPONDER_ID);
        List<CommentModel> comments = new ArrayList<>(List.of(
                new CommentModel("reporter", "Reporter", "Update", Instant.parse("2026-09-21T00:20:00Z"))));

        IncidentDetailModel detail = mapper(ADMIN).toDetail(
                assigned, comments, List.of(), SloSummaryModel.unavailable());
        comments.clear();

        assertEquals("21 Sep 2026 00:00", detail.summary().createdAt());
        assertEquals("21 Sep 2026 00:30", detail.queue().firstAssignedAt());
        assertEquals(1, detail.comments().size());
        assertThrows(UnsupportedOperationException.class, () -> detail.comments().clear());
    }

    @Test
    void rowCarriesQueueAndSuppliedSloWithoutRequiringProtectedIdentity() {
        Incident assigned = lifecycleAt(Instant.parse("2026-09-20T16:30:00Z"))
                .claim(submitted(true), RESPONDER_ID);
        SloSummaryModel slo = new SloSummaryModel("At risk", .85, false);

        IncidentRowModel row = mapper(RESPONDER).toRow(assigned, slo);

        assertEquals("21 Sep 2026 00:00", row.queueEnteredAt());
        assertEquals(slo, row.slo());
        assertEquals(IncidentPresentationMapper.ANONYMOUS_REPORTER_LABEL, row.reporterLabel());
    }

    @Test
    void everyLifecycleStateHasCentralizedDisplayLabels() {
        assertEquals("Draft", IncidentDisplayLabels.status(draft(false).status()));
        assertEquals("Submitted", IncidentDisplayLabels.status(submitted(false).status()));
        Incident assigned = lifecycleAt(Instant.parse("2026-09-20T16:01:00Z"))
                .claim(submitted(false), RESPONDER_ID);
        assertEquals("Assigned", IncidentDisplayLabels.status(assigned.status()));
        Incident resolved = lifecycleAt(Instant.parse("2026-09-20T16:02:00Z"))
                .resolve(assigned, RESPONDER_ID, "Done");
        assertEquals("Resolved", IncidentDisplayLabels.status(resolved.status()));
        Incident withdrawn = lifecycleAt(Instant.parse("2026-09-20T16:03:00Z"))
                .withdraw(submitted(false));
        assertEquals("Withdrawn", IncidentDisplayLabels.status(withdrawn.status()));
        assertEquals("Human Relations", IncidentDisplayLabels.category(IncidentCategory.HUMAN_RELATIONS));
    }

    private static IncidentPresentationMapper mapper(Account actor) {
        TestSession session = new TestSession(actor);
        AccountLookup lookup = new TestAccountLookup(Map.of(
                REPORTER_ID, REPORTER,
                RESPONDER_ID, RESPONDER,
                ADMIN_ID, ADMIN));
        return new IncidentPresentationMapper(
                lookup,
                new IncidentAuthorizationPolicy(session),
                ZoneId.of("Asia/Singapore"),
                DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm", Locale.ENGLISH));
    }

    private static Incident draft(boolean anonymous) {
        return lifecycleAt(CREATED).saveDraft(
                new IncidentId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                REPORTER_ID,
                "Printer outage",
                "The printer is unavailable",
                IncidentCategory.IT,
                anonymous);
    }

    private static Incident submitted(boolean anonymous) {
        return lifecycleAt(CREATED).submit(draft(anonymous));
    }

    private static IncidentLifecycle lifecycleAt(Instant instant) {
        return new IncidentLifecycle(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static Account account(AccountId id, String loginName, Role role) {
        return new Account(id, loginName, role, AccountStatus.ENABLED, ResponderAccess.NONE);
    }

    private static Account responder(AccountId id, String loginName, IncidentCategory category) {
        return new Account(
                id,
                loginName,
                Role.RESPONDER,
                AccountStatus.ENABLED,
                ResponderAccess.to(Set.of(category)));
    }

    private static AccountId id(String value) {
        return new AccountId(UUID.fromString(value));
    }

    /** Fixed authenticated session used by mapper tests. */
    private record TestSession(Account actor) implements SessionProvider {
        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return Optional.of(new AuthenticatedSession(actor.id(), CREATED));
        }

        @Override
        public Optional<Account> currentAccount() {
            return Optional.of(actor);
        }
    }

    /** In-memory account lookup used to resolve authorized display labels. */
    private record TestAccountLookup(Map<AccountId, Account> accounts) implements AccountLookup {
        @Override
        public Optional<Account> findById(AccountId accountId) {
            return Optional.ofNullable(accounts.get(accountId));
        }

        @Override
        public Optional<Account> findByLoginName(String loginName) {
            return accounts.values().stream().filter(account -> account.loginName().equals(loginName)).findFirst();
        }
    }
}

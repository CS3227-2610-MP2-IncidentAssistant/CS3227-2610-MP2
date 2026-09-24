package com.company.incidentdesk.application.audit;

import static com.company.incidentdesk.testutil.AuditTestData.ACTOR_ID;
import static com.company.incidentdesk.testutil.AuditTestData.reporter;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.audit.AuditActor;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.persistence.memory.InMemoryAccountRepository;

/** Tests anonymous and tombstoned audit actor labels. */
class AuditActorLabelResolverTest {
    @Test
    void tombstonedAccountUsesDeletedLabelWithoutErasingIdentifier() {
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        var deleted = new com.company.incidentdesk.domain.account.Account(
                ACTOR_ID, "deleted:" + ACTOR_ID.value(), Role.REPORTER,
                AccountStatus.DELETED, ResponderAccess.NONE);
        accounts.create(deleted);
        AuditActor actor = new AuditActor(deleted.id(), Role.REPORTER, AuditActorVisibility.STANDARD);

        assertEquals(AuditActorLabelResolver.DELETED_ACCOUNT_LABEL,
                new AuditActorLabelResolver(accounts).resolve(actor));
        assertEquals(deleted.id(), actor.accountId());
    }
    @Test
    void resolvesCurrentNonAnonymousActorName() {
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        accounts.create(reporter("Alice"));
        AuditActorLabelResolver resolver = new AuditActorLabelResolver(accounts);

        String label = resolver.resolve(new AuditActor(
                ACTOR_ID,
                Role.REPORTER,
                AuditActorVisibility.STANDARD));

        assertEquals("Alice", label);
    }

    @Test
    void anonymousReporterLabelNeverUsesAccountName() {
        InMemoryAccountRepository accounts = new InMemoryAccountRepository();
        accounts.create(reporter("IdentityMustStayHidden"));
        AuditActorLabelResolver resolver = new AuditActorLabelResolver(accounts);

        String label = resolver.resolve(new AuditActor(
                ACTOR_ID,
                Role.REPORTER,
                AuditActorVisibility.ANONYMOUS_REPORTER));

        assertEquals(AuditActorLabelResolver.ANONYMOUS_REPORTER_LABEL, label);
    }

    @Test
    void missingAccountUsesTombstonedLabel() {
        AuditActorLabelResolver resolver = new AuditActorLabelResolver(new InMemoryAccountRepository());

        String label = resolver.resolve(new AuditActor(
                ACTOR_ID,
                Role.REPORTER,
                AuditActorVisibility.STANDARD));

        assertEquals(AuditActorLabelResolver.DELETED_ACCOUNT_LABEL, label);
    }
}

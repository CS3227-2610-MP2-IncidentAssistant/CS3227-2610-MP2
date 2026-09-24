package com.company.incidentdesk.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;

import com.company.incidentdesk.application.attachment.AttachmentLimits;
import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.audit.AuditEventFactory;
import com.company.incidentdesk.application.authorization.IncidentAuthorizationPolicy;
import com.company.incidentdesk.application.session.AuthenticatedSession;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.AccountStatus;
import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.attachment.AttachmentId;
import com.company.incidentdesk.domain.audit.AuditEventId;
import com.company.incidentdesk.domain.incident.Incident;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.persistence.file.LocalApplicationStore;

/** Real services with explicitly supplied temporary storage and deterministic lifecycle time. */
public final class AttachmentFixture implements AutoCloseable {
    public static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");
    public final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    public final LocalApplicationStore store;
    public final Account reporter = account(1, Role.REPORTER, ResponderAccess.NONE);
    public final Account otherReporter = account(2, Role.REPORTER, ResponderAccess.NONE);
    public final Account responder = account(3, Role.RESPONDER, ResponderAccess.to(Set.of(IncidentCategory.IT)));
    public final Account administrator = account(4, Role.ADMINISTRATOR, ResponderAccess.NONE);
    public final Incident incident = new IncidentLifecycle(clock).submit(new IncidentId(new UUID(0, 10)),
            reporter.id(), "Printer", "Broken", IncidentCategory.IT, true);
    public final Sessions sessions = new Sessions();
    public final AttachmentService service;

    public AttachmentFixture(Path directory) {
        store = new LocalApplicationStore(directory);
        if (store.findById(reporter.id()).isEmpty()) {
            store.create(reporter);
            store.create(otherReporter);
            store.create(responder);
            store.create(administrator);
            store.create(incident);
        }
        sessions.actor = reporter;
        service = serviceWith(AttachmentLimits.DEFAULT);
    }

    public AttachmentService serviceWith(AttachmentLimits limits) {
        return new AttachmentService(sessions, store, store.attachmentStore(), new IncidentAuthorizationPolicy(sessions),
                limits, new AuditEventFactory(clock, () -> new AuditEventId(UUID.randomUUID())),
                clock, () -> new AttachmentId(UUID.randomUUID()));
    }

    public static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        image.flush();
        return bytes.toByteArray();
    }

    public static Account account(long id, Role role, ResponderAccess access) {
        return new Account(new AccountId(new UUID(0, id)), "user-" + id, role, AccountStatus.ENABLED, access);
    }

    @Override public void close() { store.close(); }

    public final class Sessions implements SessionProvider {
        public volatile Account actor;
        public volatile Instant authenticatedAt = NOW;
        @Override public Optional<Account> currentAccount() {
            Account selected = actor;
            return selected == null ? Optional.empty() : store.findById(selected.id()).filter(Account::isEnabled);
        }
        @Override public Optional<AuthenticatedSession> currentSession() {
            return currentAccount().map(account -> new AuthenticatedSession(account.id(), authenticatedAt));
        }
    }
}

package com.company.incidentdesk.application.attachment;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.company.incidentdesk.domain.account.ResponderAccess;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.attachment.AttachmentId;
import com.company.incidentdesk.domain.audit.AuditAction;
import com.company.incidentdesk.domain.audit.AuditActorVisibility;
import com.company.incidentdesk.domain.incident.IncidentLifecycle;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.persistence.AuditQuery;
import com.company.incidentdesk.persistence.AuditSortDirection;
import com.company.incidentdesk.support.AttachmentFixture;

class AttachmentServiceTest {
    @TempDir Path temporary;

    @Test
    void rejectsRealVideoAndVideoRenamedAsImageWithoutWritingData() throws Exception {
        byte[] video;
        try (var input = getClass().getResourceAsStream("/attachments/black-white-h264.mp4")) {
            video = input.readAllBytes();
        }
        try (AttachmentFixture fixture = new AttachmentFixture(temporary.resolve("store"))) {
            byte[] before = Files.readAllBytes(temporary.resolve("store/incident-desk.dat"));
            for (String name : List.of("video.mp4", "disguised.png", "disguised.jpg")) {
                Path source = Files.write(temporary.resolve(name), video);
                assertEquals(ApplicationErrorCode.VALIDATION,
                        fixture.service.add(fixture.incident.id(), source).error().orElseThrow().code());
                assertArrayEquals(video, Files.readAllBytes(source));
            }
            assertArrayEquals(before, Files.readAllBytes(temporary.resolve("store/incident-desk.dat")));
            assertTrue(fixture.service.list(fixture.incident.id()).value().orElseThrow().isEmpty());
            assertTrue(fixture.store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).isEmpty());
            try (var files = Files.list(temporary.resolve("store/attachments"))) {
                assertEquals(0, files.count());
            }
        }
    }

    @Test
    void uploadGuidanceUsesDefaultLimits() {
        try (AttachmentFixture fixture = new AttachmentFixture(temporary.resolve("store"))) {
            assertEquals("PNG/JPEG 10 MiB; 5 files and 100 MiB per incident; "
                    + "images up to 40000000 pixels.", fixture.service.uploadLimitSummary());
        }
    }

    @Test
    void uploadGuidanceUsesCustomLimitsWithoutRounding() {
        try (AttachmentFixture fixture = new AttachmentFixture(temporary.resolve("store"))) {
            AttachmentService service = fixture.serviceWith(new AttachmentLimits(1048577, 1, 3145728, 123));
            assertEquals("PNG/JPEG 1048577 bytes; 1 file and 3 MiB per incident; "
                    + "images up to 123 pixels.", service.uploadLimitSummary());
        }
    }

    @Test
    void additionIsPrivateAuditedAndSurvivesRestartAndUnrelatedWrites() throws Exception {
        Path original = Files.write(temporary.resolve("private-reporter-name.png"), AttachmentFixture.png(2, 2));
        AttachmentId id;
        try (AttachmentFixture fixture = new AttachmentFixture(temporary.resolve("store"))) {
            var result = fixture.service.add(fixture.incident.id(), original);
            assertTrue(result.isSuccess());
            assertEquals("Image.png", result.value().orElseThrow().displayName());
            assertFalse(result.toString().contains("private-reporter"));
            id = new AttachmentId(UUID.fromString(result.value().orElseThrow().id()));
            var audits = fixture.store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST);
            assertEquals(1, audits.size());
            assertEquals(AuditAction.ATTACHMENT_ADDED, audits.getFirst().action());
            assertEquals(AuditActorVisibility.ANONYMOUS_REPORTER, audits.getFirst().actor().visibility());
            assertFalse(audits.toString().contains("private-reporter"));
            fixture.store.update(fixture.reporter);
            fixture.store.update(fixture.incident);
            assertEquals(fixture.incident, fixture.store.findById(fixture.incident.id()).orElseThrow());
            assertArrayEquals(Files.readAllBytes(original), fixture.service.open(id).value().orElseThrow().content().bytes());
        }
        assertTrue(Files.exists(original));
        try (AttachmentFixture reopened = new AttachmentFixture(temporary.resolve("store"))) {
            assertEquals(1, reopened.service.list(reopened.incident.id()).value().orElseThrow().size());
            assertTrue(reopened.service.open(id).isSuccess());
        }
    }

    @Test
    void onlyEditableOwnerCanAddButAuthorizedRolesCanRead() throws Exception {
        Path original = Files.write(temporary.resolve("image.png"), AttachmentFixture.png(1, 1));
        try (AttachmentFixture fixture = new AttachmentFixture(temporary.resolve("store"))) {
            var added = fixture.service.add(fixture.incident.id(), original).value().orElseThrow();
            AttachmentId id = new AttachmentId(UUID.fromString(added.id()));
            for (var actor : List.of(fixture.otherReporter, fixture.responder, fixture.administrator)) {
                fixture.sessions.actor = actor;
                assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE,
                        fixture.service.add(fixture.incident.id(), original).error().orElseThrow().code());
            }
            fixture.sessions.actor = fixture.responder;
            assertTrue(fixture.service.open(id).isSuccess());
            var lease = fixture.service.open(id).value().orElseThrow();
            fixture.store.update(AttachmentFixture.account(3, Role.RESPONDER, ResponderAccess.NONE));
            assertFalse(lease.isAvailable());
            assertThrows(SecurityException.class, lease::content);
            assertEquals(ApplicationErrorCode.RESOURCE_UNAVAILABLE, fixture.service.open(id).error().orElseThrow().code());
            fixture.sessions.actor = null;
            assertFalse(fixture.service.list(fixture.incident.id()).isSuccess());
            assertFalse(fixture.service.open(id).isSuccess());
        }
    }

    @Test
    void assignedResolvedAndWithdrawnIncidentsRejectNewFiles() throws Exception {
        Path original = Files.write(temporary.resolve("image.png"), AttachmentFixture.png(1, 1));
        try (AttachmentFixture fixture = new AttachmentFixture(temporary.resolve("store"))) {
            IncidentLifecycle lifecycle = new IncidentLifecycle(fixture.clock);
            var assigned = lifecycle.claim(fixture.incident, fixture.responder.id());
            for (var incident : List.of(assigned, lifecycle.resolve(assigned, fixture.responder.id(), "Done"),
                    lifecycle.withdraw(fixture.incident))) {
                fixture.store.update(incident);
                assertFalse(fixture.service.add(incident.id(), original).isSuccess());
            }
            assertTrue(fixture.store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).isEmpty());
        }
    }

    @Test
    void countAndTotalLimitsFailWithoutChangingCanonicalData() throws Exception {
        byte[] bytes = AttachmentFixture.png(1, 1);
        Path original = Files.write(temporary.resolve("image.png"), bytes);
        try (AttachmentFixture fixture = new AttachmentFixture(temporary.resolve("store"))) {
            AttachmentService limited = fixture.serviceWith(new AttachmentLimits(bytes.length, 1, bytes.length, 1));
            assertTrue(limited.add(fixture.incident.id(), original).isSuccess());
            byte[] before = Files.readAllBytes(temporary.resolve("store/incident-desk.dat"));
            assertEquals(ApplicationErrorCode.VALIDATION,
                    limited.add(fixture.incident.id(), original).error().orElseThrow().code());
            assertArrayEquals(before, Files.readAllBytes(temporary.resolve("store/incident-desk.dat")));
            try (var files = Files.list(temporary.resolve("store/attachments"))) {
                assertEquals(1, files.count());
            }
        }
    }

    @Test
    void failureWritingAuditMetadataRollsBackNewBlob() throws Exception {
        Path original = Files.write(temporary.resolve("image.png"), AttachmentFixture.png(1, 1));
        Path directory = temporary.resolve("store");
        try (AttachmentFixture fixture = new AttachmentFixture(directory)) {
            Path backup = directory.resolve("incident-desk.dat.bak");
            Files.delete(backup);
            Files.createDirectory(backup);
            Files.writeString(backup.resolve("obstruction"), "test-only");
            byte[] before = Files.readAllBytes(directory.resolve("incident-desk.dat"));
            assertFalse(fixture.service.add(fixture.incident.id(), original).isSuccess());
            assertArrayEquals(before, Files.readAllBytes(directory.resolve("incident-desk.dat")));
            assertTrue(fixture.store.attachmentStore().list(fixture.incident.id()).isEmpty());
            assertTrue(fixture.store.find(AuditQuery.all(), AuditSortDirection.OLDEST_FIRST).isEmpty());
            try (var files = Files.list(directory.resolve("attachments"))) {
                assertEquals(0, files.count());
            }
        }
    }

    @Test
    void staleReadIsRejectedAfterSameAccountLogsInAgain() throws Exception {
        Path source = Files.write(temporary.resolve("image.png"), AttachmentFixture.png(1, 1));
        try (AttachmentFixture fixture = new AttachmentFixture(temporary.resolve("store"))) {
            var added = fixture.service.add(fixture.incident.id(), source).value().orElseThrow();
            var lease = fixture.service.open(new AttachmentId(UUID.fromString(added.id()))).value().orElseThrow();
            fixture.sessions.authenticatedAt = fixture.sessions.authenticatedAt.plusSeconds(1);
            assertFalse(lease.isAvailable());
        }
    }

    @Test
    void missingAndUnauthorizedAttachmentsHaveTheSameError() throws Exception {
        Path source = Files.write(temporary.resolve("image.png"), AttachmentFixture.png(1, 1));
        try (AttachmentFixture fixture = new AttachmentFixture(temporary.resolve("store"))) {
            var added = fixture.service.add(fixture.incident.id(), source).value().orElseThrow();
            fixture.sessions.actor = fixture.otherReporter;
            var denied = fixture.service.open(new AttachmentId(UUID.fromString(added.id()))).error();
            var missing = fixture.service.open(new AttachmentId(UUID.randomUUID())).error();
            assertEquals(missing, denied);
        }
    }
}

package com.company.incidentdesk.domain.comment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.domain.incident.IncidentId;

class IncidentCommentTest {
    @Test
    void preservesNonBlankTextAndRejectsWhitespaceOnlyText() {
        IncidentComment comment = new IncidentComment(
                new CommentId(UUID.randomUUID()), new IncidentId(UUID.randomUUID()),
                new AccountId(UUID.randomUUID()), Role.REPORTER, Instant.EPOCH, CommentType.ORDINARY, "  update  ");

        assertEquals("  update  ", comment.text());
        assertThrows(IllegalArgumentException.class, () -> new IncidentComment(
                new CommentId(UUID.randomUUID()), new IncidentId(UUID.randomUUID()),
                new AccountId(UUID.randomUUID()), Role.REPORTER, Instant.EPOCH, CommentType.ORDINARY, " \t "));
    }
}

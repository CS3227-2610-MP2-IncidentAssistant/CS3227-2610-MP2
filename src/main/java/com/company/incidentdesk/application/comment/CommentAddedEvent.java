package com.company.incidentdesk.application.comment;

import java.util.Objects;

import com.company.incidentdesk.application.event.ApplicationEvent;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.comment.CommentId;
import com.company.incidentdesk.domain.incident.IncidentId;

/** Post-commit comment fact without comment text or author identity. */
public record CommentAddedEvent(
        IncidentId incidentId,
        CommentId commentId,
        AccountId actorId) implements ApplicationEvent {
    public CommentAddedEvent {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(commentId, "commentId");
        Objects.requireNonNull(actorId, "actorId");
    }
}

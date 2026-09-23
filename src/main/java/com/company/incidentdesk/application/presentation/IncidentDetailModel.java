package com.company.incidentdesk.application.presentation;

import java.util.List;
import java.util.Objects;

/** Immutable incident detail data authorized and prepared for UI rendering. */
public record IncidentDetailModel(
        IncidentRowModel summary,
        String description,
        String submittedAt,
        String withdrawnAt,
        QueueSummaryModel queue,
        List<ResolutionModel> resolutions,
        List<CommentModel> comments,
        List<AttachmentModel> attachments,
        SloSummaryModel slo) {
    public IncidentDetailModel {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(withdrawnAt, "withdrawnAt");
        Objects.requireNonNull(queue, "queue");
        resolutions = List.copyOf(Objects.requireNonNull(resolutions, "resolutions"));
        comments = List.copyOf(Objects.requireNonNull(comments, "comments"));
        attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
        Objects.requireNonNull(slo, "slo");
    }
}

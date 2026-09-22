package com.company.incidentdesk.domain.incident;

import java.time.Instant;
import java.util.Objects;

import com.company.incidentdesk.domain.account.AccountId;

/** Required reporter explanation produced together with a reopened incident. */
public record ReopenExplanation(
        AccountId authorId,
        String text,
        Instant createdAt,
        int resolutionCycleNumber) {
    /**
     * Creates an explanation associated with the newly opened resolution cycle.
     *
     * @param authorId internal account identifier of the reporter
     * @param text non-blank explanation, preserved as entered
     * @param createdAt application-generated UTC reopen time
     * @param resolutionCycleNumber one-based cycle number opened by the explanation
     */
    public ReopenExplanation {
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(createdAt, "createdAt");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (resolutionCycleNumber < 2) {
            throw new IllegalArgumentException("a reopen explanation must reference a reopened cycle");
        }
    }
}

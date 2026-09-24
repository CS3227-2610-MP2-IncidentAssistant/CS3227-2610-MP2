package com.company.incidentdesk.ui.shared.components;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

import com.company.incidentdesk.application.presentation.CommentModel;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Reusable, privacy-safe incident comment list and composer. */
public final class IncidentCommentThread extends VBox {
    private final VBox entries = new VBox(12);
    private final Clock clock;
    private final TextArea composer = new TextArea();
    private final Button submit = UiComponents.action("Add comment", ActionStyle.PRIMARY);
    private Predicate<String> onSubmit = ignored -> false;
    private Function<String, CompletionStage<Boolean>> onSubmitAsync;
    private boolean composerDisabled;
    private boolean submitting;

    public IncidentCommentThread() {
        this(Clock.systemDefaultZone());
    }

    public IncidentCommentThread(Clock clock) {
        super(14);
        this.clock = Objects.requireNonNull(clock, "clock");
        getStyleClass().add("comment-thread");
        composer.setPromptText("Add a comment");
        composer.setAccessibleText("Comment text");
        submit.setOnAction(event -> submit());

        HBox actions = new HBox(submit);
        actions.setAlignment(Pos.CENTER_RIGHT);
        getChildren().addAll(entries, composer, actions);
    }

    public void setComments(List<CommentModel> comments) {
        entries.getChildren().clear();
        Objects.requireNonNull(comments, "comments")
                .forEach(comment -> entries.getChildren().add(entry(comment)));
        if (comments.isEmpty()) {
            Label empty = new Label("No comments yet");
            empty.getStyleClass().add("muted");
            entries.getChildren().add(empty);
        }
    }

    /** Sets a handler that returns true only when the comment was accepted. */
    public void setOnSubmit(Predicate<String> onSubmit) {
        this.onSubmit = Objects.requireNonNull(onSubmit, "onSubmit");
        onSubmitAsync = null;
    }

    /** Sets an asynchronous handler; the draft is cleared only after successful completion. */
    public void setOnSubmitAsync(Function<String, CompletionStage<Boolean>> onSubmit) {
        onSubmitAsync = Objects.requireNonNull(onSubmit, "onSubmit");
    }

    public void setComposerDisabled(boolean disabled) {
        composerDisabled = disabled;
        updateComposerAvailability();
    }

    private HBox entry(CommentModel comment) {
        // Derive avatars only from the already-redacted display label.
        String initials = comment.authorLabel().isBlank()
                ? "?" : comment.authorLabel().strip().substring(0, 1).toUpperCase(Locale.ROOT);
        return UiComponents.comment(initials, comment.authorLabel(), comment.authorRoleLabel(),
                comment.createdAt(), clock, comment.text());
    }

    private void submit() {
        String text = composer.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        if (onSubmitAsync == null) {
            if (onSubmit.test(text)) {
                composer.clear();
            }
            return;
        }
        if (submitting || composerDisabled) {
            return;
        }
        submitting = true;
        updateComposerAvailability();
        CompletionStage<Boolean> result;
        try {
            result = Objects.requireNonNull(onSubmitAsync.apply(text), "comment completion");
        } catch (RuntimeException exception) {
            submitting = false;
            updateComposerAvailability();
            return;
        }
        result.whenComplete((accepted, failure) -> Platform.runLater(() -> {
            if (failure == null && Boolean.TRUE.equals(accepted)) {
                composer.clear();
            }
            submitting = false;
            updateComposerAvailability();
        }));
    }

    private void updateComposerAvailability() {
        composer.setDisable(composerDisabled || submitting);
        submit.setDisable(composerDisabled || submitting);
    }
}

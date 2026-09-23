package com.company.incidentdesk.ui.shared.components;

import java.util.List;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

import com.company.incidentdesk.application.presentation.CommentModel;

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
    }

    public void setComposerDisabled(boolean disabled) {
        composer.setDisable(disabled);
        submit.setDisable(disabled);
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
        if (onSubmit.test(text)) {
            composer.clear();
        }
    }
}

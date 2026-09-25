package com.company.incidentdesk.ui.shared.components;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Factories for small, presentation-only components shared by role pages. */
public final class UiComponents {
    private static final DateTimeFormatter LOCAL_DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private UiComponents() {
    }

    public static Label badge(String text, SemanticTone tone) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", tone.styleClass());
        badge.setAccessibleText(text);
        return badge;
    }

    public static Label avatar(String initials, String personLabel) {
        Label avatar = new Label(Objects.requireNonNull(initials, "initials"));
        avatar.getStyleClass().add("avatar");
        avatar.setAccessibleText("Avatar for " + Objects.requireNonNull(personLabel, "personLabel"));
        return avatar;
    }

    public static Button action(String text, ActionStyle style) {
        Button button = new Button(text);
        button.getStyleClass().add(style.styleClass());
        button.setAccessibleText(text);
        return button;
    }

    public static VBox metricCard(String label, String value, String detail) {
        Label metricLabel = new Label(label);
        metricLabel.getStyleClass().add("metric-label");
        Label metricValue = new Label(value);
        metricValue.getStyleClass().add("metric-value");
        Label metricDetail = new Label(detail);
        metricDetail.getStyleClass().add("muted");
        metricDetail.setWrapText(true);

        VBox card = new VBox(6, metricLabel, metricValue, metricDetail);
        card.getStyleClass().add("metric-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    public static VBox feedback(String title, String detail, FeedbackType type) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        Label description = new Label(detail);
        description.setWrapText(true);

        VBox feedback = new VBox(7, heading, description);
        feedback.setAlignment(Pos.TOP_LEFT);
        feedback.getStyleClass().addAll("feedback-card", type.styleClass());
        return feedback;
    }

    public static ValidatedField field(String label, Node control) {
        return new ValidatedField(label, control);
    }

    public static VBox attachmentTile(String title, String detail) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        Label description = new Label(detail);
        description.setWrapText(true);
        VBox tile = new VBox(7, heading, description);
        tile.getStyleClass().add("attachment-tile");
        return tile;
    }

    public static HBox timelineEvent(String description) {
        Label marker = new Label();
        marker.getStyleClass().add("timeline-marker");
        Label event = new Label(description);
        event.setWrapText(true);
        HBox timelineEvent = new HBox(10, marker, event);
        timelineEvent.setAlignment(Pos.CENTER_LEFT);
        return timelineEvent;
    }

    public static HBox comment(
            String initials,
            String authorName,
            String roleName,
            Instant sentAt,
            Clock clock,
            String messageText) {
        Label avatar = avatar(initials, authorName);

        Label author = new Label(authorName);
        author.getStyleClass().add("comment-author");
        Label role = new Label("· " + roleName);
        role.getStyleClass().add("muted");
        HBox identity = new HBox(5, author, role);

        ZonedDateTime reference = ZonedDateTime.now(Objects.requireNonNull(clock, "clock"));
        Label sentAtLabel = new Label(UiDateTimeFormatter.formatRelative(sentAt, reference));
        sentAtLabel.getStyleClass().addAll("muted", "comment-date");
        Label message = new Label(messageText);
        message.setWrapText(true);

        VBox body = new VBox(4, identity, sentAtLabel, message);
        body.getStyleClass().add("comment-body");
        HBox comment = new HBox(10, avatar, body);
        HBox.setHgrow(body, Priority.ALWAYS);
        return comment;
    }

    public static SloProgressBar sloProgress(double progress, String accessibleDescription) {
        return new SloProgressBar(progress, accessibleDescription);
    }

    public static VBox panel(String title, Node... content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");

        VBox body = new VBox(12, content);
        body.getStyleClass().add("panel-body");
        VBox panel = new VBox(heading, body);
        panel.getStyleClass().add("panel");
        VBox.setMargin(heading, new Insets(18, 20, 0, 20));
        return panel;
    }

    public static DateTimeFormatter localDateTimeFormatter() {
        return LOCAL_DATE_TIME;
    }

    /**
     * Shows a blocking confirmation dialog for a consequential action.
     *
     * @param dialogId lookup id placed on the dialog pane so tests can find the showing window
     * @return true when the confirming button was chosen
     */
    public static boolean confirm(String title, String header, String content, String confirmText, String dialogId) {
        ButtonType confirmButton = new ButtonType(
                Objects.requireNonNull(confirmText, "confirmText"), ButtonBar.ButtonData.OK_DONE);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.CANCEL, confirmButton);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().setId(Objects.requireNonNull(dialogId, "dialogId"));
        return dialog.showAndWait().filter(confirmButton::equals).isPresent();
    }
}

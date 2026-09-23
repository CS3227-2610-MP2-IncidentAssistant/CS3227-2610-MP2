package com.company.incidentdesk.ui.shared.components;

import java.util.Objects;

import com.company.incidentdesk.application.event.Subscription;
import com.company.incidentdesk.application.notification.Notification;
import com.company.incidentdesk.application.notification.NotificationInbox;
import com.company.incidentdesk.application.notification.NotificationSnapshot;
import com.company.incidentdesk.domain.account.AccountId;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Popup;
import javafx.stage.Window;

/** Shared, recipient-bound notification tray with an unseen-count badge. */
public final class NotificationCenter extends VBox implements AutoCloseable {
    private static final double SPACING = 10;
    private static final double POPOVER_WIDTH = 360;
    private static final double POPOVER_OFFSET = 6;

    private final NotificationInbox inbox;
    private final AccountId recipientId;
    private final Button toggle = UiComponents.action("Notifications", ActionStyle.SECONDARY);
    private final Label unseenBadge = UiComponents.badge("0", SemanticTone.INFO);
    private final VBox entries = new VBox(SPACING);
    private final ScrollPane tray = new ScrollPane(entries);
    private final VBox popoverContent = new VBox(tray);
    private final Popup popover = new Popup();
    private final Subscription subscription;
    private NotificationSnapshot displayedSnapshot = new NotificationSnapshot(java.util.List.of(), 0);
    private long seenSequenceAtOpen = -1;

    public NotificationCenter(NotificationInbox inbox, AccountId recipientId) {
        super(SPACING);
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId");

        toggle.setId("notification-toggle");
        toggle.setAccessibleText("Open notifications, 0 unseen");
        toggle.setOnAction(event -> toggleTray());
        unseenBadge.setId("notification-unseen-count");

        SVGPath bell = new SVGPath();
        bell.setContent("M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22Zm7-5v-6a7 7 0 0 0-5-6.71V3a2 2 0 0 0-4 0v1.29A7 7 0 0 0 5 11v6l-2 2h18l-2-2Z");
        bell.getStyleClass().add("notification-bell");
        toggle.setText("");
        toggle.setGraphic(bell);
        AnchorPane unreadOverlay = new AnchorPane(unseenBadge);
        unreadOverlay.setId("notification-unread-overlay");
        unreadOverlay.setMouseTransparent(true);
        AnchorPane.setTopAnchor(unseenBadge, 1.0);
        AnchorPane.setRightAnchor(unseenBadge, 1.0);
        StackPane header = new StackPane(toggle, unreadOverlay);
        header.setId("notification-bell-stack");
        header.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        tray.setId("notification-tray");
        tray.setAccessibleText("Notification tray");
        tray.setFitToWidth(true);
        tray.setVisible(false);
        tray.setManaged(false);
        tray.getStyleClass().add("notification-tray");
        entries.getStyleClass().add("notification-list");
        popoverContent.setPrefWidth(POPOVER_WIDTH);
        popoverContent.getStyleClass().add("notification-popover");
        popover.getContent().add(popoverContent);
        popover.setAutoFix(true);
        popover.setAutoHide(true);
        popover.setHideOnEscape(true);
        popover.setOnHidden(event -> closePopover());
        getChildren().add(header);
        getStyleClass().add("notification-center");

        subscription = inbox.subscribe(recipientId, this::acceptSnapshot);
    }

    private void toggleTray() {
        if (tray.isVisible()) {
            popover.hide();
            closePopover();
            return;
        }
        tray.setVisible(true);
        tray.setManaged(true);
        seenSequenceAtOpen = displayedSnapshot.lastSeenSequence();
        render(displayedSnapshot);
        toggle.setAccessibleText("Close notifications");
        showPopover();
        long displayedThrough = displayedSnapshot.latestSequence();
        inbox.markSeenThrough(recipientId, displayedThrough);
    }

    private void showPopover() {
        if (toggle.getScene() == null || toggle.getScene().getWindow() == null) {
            return;
        }
        Window owner = toggle.getScene().getWindow();
        Bounds anchor = toggle.localToScreen(toggle.getBoundsInLocal());
        if (anchor == null) {
            return;
        }
        popover.show(owner, anchor.getMaxX() - POPOVER_WIDTH, anchor.getMaxY() + POPOVER_OFFSET);
        popoverContent.getScene().getStylesheets().setAll(toggle.getScene().getStylesheets());
        popoverContent.applyCss();
    }

    private void closePopover() {
        tray.setVisible(false);
        tray.setManaged(false);
        seenSequenceAtOpen = -1;
        render(displayedSnapshot);
        toggle.setAccessibleText("Open notifications, " + displayedSnapshot.unseenCount() + " unseen");
    }

    private void acceptSnapshot(NotificationSnapshot snapshot) {
        if (Platform.isFxApplicationThread()) {
            render(snapshot);
        } else {
            Platform.runLater(() -> render(snapshot));
        }
    }

    private void render(NotificationSnapshot snapshot) {
        displayedSnapshot = snapshot;
        long unseen = snapshot.unseenCount();
        unseenBadge.setText(Long.toString(unseen));
        unseenBadge.setVisible(unseen > 0);
        unseenBadge.setManaged(unseen > 0);
        toggle.setAccessibleText("Open notifications, " + unseen + " unseen");

        entries.getChildren().clear();
        if (snapshot.notifications().isEmpty()) {
            Label empty = new Label("No notifications yet.");
            empty.getStyleClass().add("muted");
            entries.getChildren().add(empty);
            return;
        }
        long seenThrough = seenSequenceAtOpen >= 0
                ? seenSequenceAtOpen : snapshot.lastSeenSequence();
        snapshot.notifications().reversed().stream()
                .map(notification -> notificationEntry(
                        notification, notification.sequence() > seenThrough))
                .forEach(entries.getChildren()::add);
    }

    private HBox notificationEntry(Notification notification, boolean unseen) {
        Label message = new Label(notification.message());
        message.setWrapText(true);
        Label timestamp = new Label(UiComponents.localDateTimeFormatter().format(notification.createdAt()));
        timestamp.getStyleClass().addAll("muted", "notification-time");
        VBox body = new VBox(4, message, timestamp);
        HBox.setHgrow(body, Priority.ALWAYS);
        HBox entry = new HBox(10, body);
        entry.setAlignment(Pos.CENTER_LEFT);
        if (unseen) {
            Region dot = new Region();
            dot.getStyleClass().add("notification-unseen-dot");
            entry.getChildren().add(dot);
        }
        entry.getStyleClass().add("notification-entry");
        String state = unseen ? "Unseen notification, " : "Notification, ";
        entry.setAccessibleText(state + notification.message() + ", " + timestamp.getText());
        return entry;
    }

    int displayedUnseenMarkerCount() {
        return (int) entries.getChildren().stream()
                .filter(entry -> entry.lookup(".notification-unseen-dot") != null)
                .count();
    }

    @Override
    public void close() {
        popover.hide();
        subscription.close();
    }
}

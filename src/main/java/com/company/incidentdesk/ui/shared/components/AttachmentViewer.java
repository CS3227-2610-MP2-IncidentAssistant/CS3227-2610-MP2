package com.company.incidentdesk.ui.shared.components;

import java.io.ByteArrayInputStream;
import java.util.Objects;

import com.company.incidentdesk.application.attachment.AttachmentContent;
import com.company.incidentdesk.application.attachment.AttachmentRead;
import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.attachment.AttachmentId;
import com.company.incidentdesk.domain.attachment.AttachmentType;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

/** In-app viewer. Access expiry and detachment discard image pixels and dispose native playback. */
public final class AttachmentViewer extends VBox implements AutoCloseable {
    private static final Duration ACCESS_CHECK_INTERVAL = Duration.millis(250);
    private final AttachmentService service;
    private final Timeline accessChecks;
    private AttachmentRead activeRead;
    private MediaPlayer player;
    private Task<ApplicationResult<AttachmentRead>> load;
    private long revision;

    public AttachmentViewer(AttachmentService service) {
        super(12);
        this.service = Objects.requireNonNull(service, "service");
        accessChecks = new Timeline(new KeyFrame(ACCESS_CHECK_INTERVAL, event -> {
            if (activeRead != null && !activeRead.isAvailable()) {
                unavailable();
            }
        }));
        accessChecks.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((observable, oldScene, scene) -> {
            if (scene == null) {
                close();
            }
        });
        setAccessibleText("Attachment viewer");
    }

    public void show(AttachmentId id) {
        close();
        long request = revision;
        getChildren().setAll(UiComponents.feedback("Loading attachment", "Reading authorized content.", FeedbackType.LOADING));
        Task<ApplicationResult<AttachmentRead>> task = new Task<>() {
            @Override protected ApplicationResult<AttachmentRead> call() { return service.open(id); }
        };
        load = task;
        task.setOnSucceeded(event -> {
            if (request == revision) {
                task.getValue().value().ifPresentOrElse(this::render, this::unavailable);
            }
        });
        task.setOnFailed(event -> { if (request == revision) unavailable(); });
        Thread.startVirtualThread(task);
    }

    private void render(AttachmentRead read) {
        try {
            activeRead = read;
            AttachmentContent content = read.content();
            if (content.type() == AttachmentType.MP4) {
                renderVideo(read);
            } else {
                renderImage(content);
            }
            if (activeRead != null) {
                accessChecks.playFromStart();
            }
        } catch (RuntimeException | LinkageError exception) {
            unavailable();
        }
    }

    private void renderImage(AttachmentContent content) {
        Image image = new Image(new ByteArrayInputStream(content.bytes()), 1200, 900, true, true);
        if (image.isError()) {
            unavailable();
            return;
        }
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.fitWidthProperty().bind(widthProperty());
        view.setFitHeight(600);
        view.setAccessibleText("Attached image");
        getChildren().setAll(view);
    }

    private void renderVideo(AttachmentRead read) {
        Media media = new Media(read.mediaSource());
        media.setOnError(this::unavailable);
        player = new MediaPlayer(media);
        player.setOnError(this::unavailable);
        if (media.getError() != null || player.getError() != null) {
            unavailable();
            return;
        }
        MediaView view = new MediaView(player);
        view.setOnError(event -> unavailable());
        view.setPreserveRatio(true);
        view.fitWidthProperty().bind(widthProperty());
        view.setFitHeight(600);
        view.setAccessibleText("Attached video");
        Button play = UiComponents.action("Play", ActionStyle.PRIMARY);
        play.setOnAction(event -> {
            if (activeRead != null && activeRead.isAvailable() && player != null) {
                player.play();
            } else {
                unavailable();
            }
        });
        Button pause = UiComponents.action("Pause", ActionStyle.SECONDARY);
        pause.setOnAction(event -> { if (player != null) player.pause(); });
        getChildren().setAll(view, new FlowPane(8, 8, play, pause));
    }

    private void unavailable() {
        close();
        getChildren().setAll(UiComponents.feedback("Attachment unavailable",
                "This attachment cannot be displayed. Check access and supported media formats.", FeedbackType.ERROR));
    }

    @Override
    public void close() {
        revision++;
        if (load != null) {
            load.cancel();
            load = null;
        }
        accessChecks.stop();
        activeRead = null;
        if (player != null) {
            player.dispose();
            player = null;
        }
        getChildren().clear();
    }
}

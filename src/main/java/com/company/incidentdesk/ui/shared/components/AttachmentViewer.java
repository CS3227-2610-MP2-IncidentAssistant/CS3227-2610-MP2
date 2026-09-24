package com.company.incidentdesk.ui.shared.components;

import java.io.ByteArrayInputStream;
import java.util.Objects;

import com.company.incidentdesk.application.attachment.AttachmentContent;
import com.company.incidentdesk.application.attachment.AttachmentRead;
import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.attachment.AttachmentId;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** In-app image viewer. Access expiry and detachment discard image pixels. */
public final class AttachmentViewer extends VBox implements AutoCloseable {
    private static final Duration ACCESS_CHECK_INTERVAL = Duration.millis(250);
    private final AttachmentService service;
    private final Timeline accessChecks;
    private AttachmentRead activeRead;
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
            if (!content.type().isSupported()) {
                unavailable();
                return;
            }
            renderImage(content);
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

    private void unavailable() {
        close();
        getChildren().setAll(UiComponents.feedback("Attachment unavailable",
                "This attachment cannot be displayed. Only PNG and JPEG images are supported; check your access.", FeedbackType.ERROR));
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
        getChildren().clear();
    }
}

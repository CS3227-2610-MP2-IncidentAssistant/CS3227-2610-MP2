package com.company.incidentdesk.ui.shared.components;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.Consumer;

import com.company.incidentdesk.application.attachment.AttachmentService;
import com.company.incidentdesk.application.presentation.AttachmentModel;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.attachment.AttachmentId;
import com.company.incidentdesk.domain.incident.IncidentId;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/** Shared attachment list/uploader for a persisted incident, independent of any role page. */
public final class AttachmentPane extends VBox implements AutoCloseable {
    private final AttachmentService service;
    private final IncidentId incidentId;
    private final VBox entries = new VBox(8);
    private final VBox feedback = new VBox();
    private final AttachmentViewer viewer;
    private final Button add = UiComponents.action("Add attachment", ActionStyle.SECONDARY);
    private final Timeline accessChecks;
    private BooleanSupplier authorized = () -> false;
    private Task<?> activeTask;
    private long revision;

    public AttachmentPane(AttachmentService service, IncidentId incidentId) {
        super(12);
        this.service = Objects.requireNonNull(service, "service");
        this.incidentId = Objects.requireNonNull(incidentId, "incidentId");
        viewer = new AttachmentViewer(service);
        Label privacy = new Label("Images and videos may reveal your identity through their content or embedded metadata.");
        privacy.setWrapText(true);
        add.setOnAction(event -> chooseAttachment());
        getChildren().setAll(UiComponents.panel("Attachments", privacy, add, feedback, entries, viewer));
        accessChecks = new Timeline(new KeyFrame(Duration.millis(250), event -> {
            if (!authorized.getAsBoolean()) {
                unavailable();
            }
        }));
        accessChecks.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((observable, previous, scene) -> {
            if (scene == null) {
                close();
            } else {
                refresh();
            }
        });
        add.setDisable(true);
    }

    public void refresh() {
        close();
        authorized = service.viewGuard(incidentId);
        if (!authorized.getAsBoolean()) {
            unavailable();
            return;
        }
        accessChecks.playFromStart();
        load(() -> service.list(incidentId), this::showRows);
    }

    /** The caller must save/submit a new incident before attaching its selected files. */
    public void addFile(Path source) {
        if (activeTask != null || !authorized.getAsBoolean() || !service.canAdd(incidentId)) {
            return;
        }
        load(() -> service.add(incidentId, source), result -> {
            if (result.isSuccess()) {
                refresh();
            } else {
                add.setDisable(!service.canAdd(incidentId));
                feedback.getChildren().setAll(UiComponents.feedback("Attachment not added",
                        "Check access, file format and limits: " + service.uploadLimitSummary(),
                        FeedbackType.ERROR));
            }
        });
    }

    private <T> void load(Supplier<ApplicationResult<T>> operation,
            Consumer<ApplicationResult<T>> completion) {
        long request = revision;
        add.setDisable(true);
        feedback.getChildren().setAll(UiComponents.feedback("Loading attachments", "Please wait.", FeedbackType.LOADING));
        Task<ApplicationResult<T>> task = new Task<>() {
            @Override protected ApplicationResult<T> call() { return operation.get(); }
        };
        activeTask = task;
        task.setOnSucceeded(event -> {
            if (request != revision) {
                return;
            }
            activeTask = null;
            if (!authorized.getAsBoolean()) {
                unavailable();
                return;
            }
            completion.accept(task.getValue());
        });
        task.setOnFailed(event -> { if (request == revision) unavailable(); });
        Thread.startVirtualThread(task);
    }

    private void showRows(ApplicationResult<List<AttachmentModel>> result) {
        if (!result.isSuccess()) {
            unavailable();
            return;
        }
        feedback.getChildren().clear();
        entries.getChildren().clear();
        for (AttachmentModel model : result.value().orElseThrow()) {
            VBox tile = UiComponents.attachmentTile(model.displayName(),
                    model.mediaType() + " · " + model.sizeBytes() + " bytes");
            Button open = UiComponents.action("View attachment", ActionStyle.SECONDARY);
            open.setOnAction(event -> {
                if (authorized.getAsBoolean()) {
                    viewer.show(new AttachmentId(UUID.fromString(model.id())));
                } else {
                    unavailable();
                }
            });
            tile.getChildren().add(open);
            entries.getChildren().add(tile);
        }
        if (entries.getChildren().isEmpty()) {
            feedback.getChildren().setAll(UiComponents.feedback("No attachments",
                    "The reporter can add supported files while the incident is editable.", FeedbackType.EMPTY));
        }
        add.setDisable(!service.canAdd(incidentId));
    }

    private void chooseAttachment() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an image or video");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Supported images and videos", "*.png", "*.jpg", "*.jpeg", "*.mp4"));
        File selected = chooser.showOpenDialog(getScene().getWindow());
        if (selected != null) {
            addFile(selected.toPath());
        }
    }

    private void unavailable() {
        close();
        feedback.getChildren().setAll(UiComponents.feedback("Attachments unavailable",
                "Check your session and refresh the incident.", FeedbackType.ERROR));
    }

    @Override
    public void close() {
        revision++;
        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }
        accessChecks.stop();
        viewer.close();
        entries.getChildren().clear();
        feedback.getChildren().clear();
        add.setDisable(true);
        authorized = () -> false;
    }
}

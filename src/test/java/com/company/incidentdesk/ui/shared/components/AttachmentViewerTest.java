package com.company.incidentdesk.ui.shared.components;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.company.incidentdesk.domain.attachment.AttachmentId;
import com.company.incidentdesk.support.AttachmentFixture;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;

class AttachmentViewerTest {
    @TempDir Path temporary;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    void imageIsRenderedAndDiscardedAfterLogout() throws Exception {
        Path source = Files.write(temporary.resolve("image.png"), AttachmentFixture.png(2, 2));
        try (AttachmentFixture fixture = new AttachmentFixture(temporary.resolve("store"))) {
            AttachmentId id = new AttachmentId(UUID.fromString(fixture.service.add(fixture.incident.id(), source)
                    .value().orElseThrow().id()));
            AttachmentViewer viewer = onFx(() -> new AttachmentViewer(fixture.service));
            try {
                CountDownLatch image = new CountDownLatch(1);
                CountDownLatch cleared = new CountDownLatch(1);
                onFx(() -> {
                    new Scene(viewer, 640, 480);
                    viewer.getChildren().addListener((ListChangeListener<Node>) change -> {
                        if (viewer.getChildren().stream().anyMatch(ImageView.class::isInstance)) {
                            image.countDown();
                        } else if (image.getCount() == 0) {
                            cleared.countDown();
                        }
                    });
                    viewer.show(id);
                    return null;
                });
                assertTrue(image.await(10, TimeUnit.SECONDS));
                fixture.sessions.actor = null;
                assertTrue(cleared.await(5, TimeUnit.SECONDS));
                assertFalse(onFx(() -> viewer.getChildren().stream().anyMatch(ImageView.class::isInstance)));
            } finally {
                onFx(() -> { viewer.close(); return null; });
            }
        }
    }

    @Test
    void unavailableAttachmentShowsSafeFeedback() throws Exception {
        try (AttachmentFixture fixture = new AttachmentFixture(temporary.resolve("store"))) {
            AttachmentViewer viewer = onFx(() -> new AttachmentViewer(fixture.service));
            CountDownLatch unavailable = new CountDownLatch(1);
            try {
                onFx(() -> {
                    new Scene(viewer, 640, 480);
                    viewer.getChildren().addListener((ListChangeListener<Node>) change -> {
                        if (viewer.getChildren().stream().anyMatch(node -> node.getStyleClass().contains(FeedbackType.ERROR.styleClass()))) {
                            unavailable.countDown();
                        }
                    });
                    viewer.show(new AttachmentId(UUID.randomUUID()));
                    return null;
                });
                assertTrue(unavailable.await(10, TimeUnit.SECONDS));
            } finally {
                onFx(() -> { viewer.close(); return null; });
            }
        }
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}

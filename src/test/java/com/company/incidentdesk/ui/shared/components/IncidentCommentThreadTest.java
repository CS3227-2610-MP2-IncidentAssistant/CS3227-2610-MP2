package com.company.incidentdesk.ui.shared.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.presentation.CommentModel;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

class IncidentCommentThreadTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    void rendersAnonymousAuthorRoleAvatarAndLocalTime() throws Exception {
        runOnJavaFx(() -> {
            Instant timestamp = Instant.parse("2026-09-23T08:00:00Z");
            IncidentCommentThread thread = new IncidentCommentThread(
                    Clock.fixed(timestamp, ZoneOffset.ofHours(8)));
            thread.setComments(List.of(new CommentModel(
                    "Anonymous reporter", "Reporter", "Still broken", timestamp)));

            VBox entries = (VBox) thread.getChildren().getFirst();
            HBox comment = (HBox) entries.getChildren().getFirst();
            assertEquals("A", ((Label) comment.getChildren().getFirst()).getText());
            VBox body = (VBox) comment.getChildren().get(1);
            HBox identity = (HBox) body.getChildren().getFirst();
            assertEquals("Anonymous reporter", ((Label) identity.getChildren().getFirst()).getText());
            assertEquals("· Reporter", ((Label) identity.getChildren().get(1)).getText());
            assertEquals("Today, 16:00", ((Label) body.getChildren().get(1)).getText());
            assertEquals("Still broken", ((Label) body.getChildren().get(2)).getText());
        });
    }

    @Test
    void preservesFailedSubmissionAndClearsSuccessfulSubmission() throws Exception {
        runOnJavaFx(() -> {
            IncidentCommentThread thread = new IncidentCommentThread();
            TextArea composer = (TextArea) thread.getChildren().get(1);
            HBox actions = (HBox) thread.getChildren().get(2);
            Button submit = (Button) actions.getChildren().getFirst();
            composer.setText("Keep this explanation");
            thread.setOnSubmit(text -> false);
            submit.fire();
            assertEquals("Keep this explanation", composer.getText());
            thread.setOnSubmit(text -> true);
            submit.fire();
            assertEquals("", composer.getText());
        });
    }

    @Test
    void asynchronousSubmissionKeepsDraftOnFailureAndClearsItOnSuccess() throws Exception {
        CompletableFuture<Boolean> failed = new CompletableFuture<>();
        CompletableFuture<Boolean> succeeded = new CompletableFuture<>();
        IncidentCommentThread thread = new IncidentCommentThread();
        runOnJavaFx(() -> {
            TextArea composer = (TextArea) thread.getChildren().get(1);
            Button submit = (Button) ((HBox) thread.getChildren().get(2)).getChildren().getFirst();
            composer.setText("Keep until accepted");
            thread.setOnSubmitAsync(ignored -> failed);
            submit.fire();
            assertTrue(submit.isDisabled());
        });
        failed.complete(false);
        runOnJavaFx(() -> {
            TextArea composer = (TextArea) thread.getChildren().get(1);
            Button submit = (Button) ((HBox) thread.getChildren().get(2)).getChildren().getFirst();
            assertEquals("Keep until accepted", composer.getText());
            assertTrue(!submit.isDisabled());
            thread.setOnSubmitAsync(ignored -> succeeded);
            submit.fire();
            assertTrue(submit.isDisabled());
        });
        succeeded.complete(true);
        runOnJavaFx(() -> {
            TextArea composer = (TextArea) thread.getChildren().get(1);
            Button submit = (Button) ((HBox) thread.getChildren().get(2)).getChildren().getFirst();
            assertEquals("", composer.getText());
            assertTrue(!submit.isDisabled());
        });
    }

    private static void runOnJavaFx(Runnable assertions) throws Exception {
        FutureTask<Void> task = new FutureTask<>(assertions, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}

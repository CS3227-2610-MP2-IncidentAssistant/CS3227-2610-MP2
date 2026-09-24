package com.company.incidentdesk.ui.navigation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.account.PasswordChangeResult;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;

class PasswordChangeDialogTest {
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
    void growsWhenValidationFeedbackAppears() throws Exception {
        onFx(() -> {
            PasswordChangeDialog dialog = new PasswordChangeDialog(
                    (current, replacement, confirmation) -> PasswordChangeResult.INVALID_CURRENT_PASSWORD);
            dialog.show();
            double initialHeight = dialog.getHeight();

            ((Button) dialog.getDialogPane().lookup("#confirm-password-change")).fire();

            Node error = dialog.getDialogPane().lookup(".field-error");
            assertTrue(error != null && error.isVisible() && error.isManaged());
            assertTrue(dialog.getHeight() > initialHeight);
            dialog.close();
            return null;
        });
    }

    private static <T> T onFx(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}

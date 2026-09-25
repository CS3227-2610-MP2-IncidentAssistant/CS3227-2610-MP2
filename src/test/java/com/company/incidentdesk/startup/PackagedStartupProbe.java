package com.company.incidentdesk.startup;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Test-only agent: observes startup without changing the production launcher. */
public final class PackagedStartupProbe {
    static final String SUCCESS_MARKER = "PACKAGED_APPLICATION_WINDOW_SHOWN";
    private static final long POLL_INTERVAL_MILLIS = 100;

    private PackagedStartupProbe() {
    }

    public static void premain(String arguments) {
        var observer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "packaged-startup-observer");
            thread.setDaemon(true);
            return thread;
        });
        observer.scheduleWithFixedDelay(() -> {
            try {
                Platform.runLater(() -> {
                    boolean applicationShown = Window.getWindows().stream().anyMatch(window ->
                            window instanceof Stage stage && stage.isShowing()
                                    && "Incident Desk".equals(stage.getTitle()) && stage.getScene() != null);
                    if (applicationShown) {
                        observer.shutdown();
                        System.out.println(SUCCESS_MARKER);
                        Platform.exit();
                    }
                });
            } catch (IllegalStateException notStartedYet) {
                // premain runs before the application initializes JavaFX; retry until it does.
            }
        }, 0, POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }
}

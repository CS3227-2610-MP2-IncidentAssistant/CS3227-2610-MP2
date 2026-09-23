package com.company.incidentdesk.ui.responder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.presentation.ResponderDashboardModel;
import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.ui.responder.ResponderDashboardPresenterTest.MutableSessions;
import com.company.incidentdesk.ui.shared.components.IncidentTable;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;

class ResponderPageTest {
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
    void rendersSafeRowsPreservesSelectionAndNavigatesWithOnlyIdentifier() throws Exception {
        Fixture fixture = onFx(() -> new Fixture(() -> {
            assertFalse(Platform.isFxApplicationThread());
            return ApplicationResult.success(populated());
        }));
        refreshAndWait(fixture);
        onFx(() -> {
            assertEquals("Anonymous reporter", fixture.eligible.getColumns().get(3).getCellData(0));
            assertTrue(fixture.eligible.getColumns().stream().noneMatch(column -> column.isSortable()));
            assertTrue(fixture.assigned.getItems().isEmpty());
            fixture.eligible.getSelectionModel().selectFirst();
            assertFalse(fixture.open.isDisabled());
            fixture.open.fire();
            assertEquals(List.of(ResponderDashboardPresenterTest.row().id()), fixture.opened);
            return null;
        });
        refreshAndWait(fixture);
        onFx(() -> {
            assertEquals(ResponderDashboardPresenterTest.row(), fixture.eligible.getSelectionModel().getSelectedItem());
            fixture.sessions.account = null;
            fixture.open.fire();
            assertEquals(1, fixture.opened.size());
            assertTrue(fixture.eligible.getItems().isEmpty());
            assertTrue(fixture.open.isDisabled());
            return null;
        });
    }

    @Test
    void loadingIsVisibleAndUnexpectedFailureIsNeutral() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        Fixture fixture = onFx(() -> new Fixture(() -> {
            try {
                assertTrue(release.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            throw new IllegalStateException("private-file-path and anonymous identity");
        }));
        CountDownLatch done = onFx(() -> watchRefresh(fixture));
        try {
            onFx(() -> {
                assertTrue(fixture.refresh.isDisabled());
                assertTrue(fixture.open.isDisabled());
                assertEquals("Loading incidents", feedbackHeading(fixture));
                assertTrue(fixture.eligible.getItems().isEmpty());
                return null;
            });
        } finally {
            release.countDown();
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            assertEquals("Incidents unavailable", feedbackHeading(fixture));
            assertTrue(fixture.eligible.getItems().isEmpty());
            assertFalse(fixture.refresh.isDisabled());
            return null;
        });
    }

    @Test
    void successfulEmptyResultHasEmptyTablesWithoutError() throws Exception {
        Fixture fixture = onFx(() -> new Fixture(() -> ApplicationResult.success(ResponderDashboardModel.empty())));
        refreshAndWait(fixture);
        onFx(() -> {
            assertTrue(fixture.feedback.getChildren().isEmpty());
            assertTrue(fixture.eligible.getItems().isEmpty());
            assertTrue(fixture.assigned.getItems().isEmpty());
            assertNull(fixture.eligible.getSelectionModel().getSelectedItem());
            assertTrue(fixture.open.isDisabled());
            return null;
        });
    }

    private static ResponderDashboardModel populated() {
        return new ResponderDashboardModel(List.of(ResponderDashboardPresenterTest.row()), List.of());
    }

    private static String feedbackHeading(Fixture fixture) {
        VBox card = (VBox) fixture.feedback.getChildren().getFirst();
        return ((Label) card.getChildren().getFirst()).getText();
    }

    private static void refreshAndWait(Fixture fixture) throws Exception {
        CountDownLatch done = onFx(() -> watchRefresh(fixture));
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }

    private static CountDownLatch watchRefresh(Fixture fixture) {
        CountDownLatch done = new CountDownLatch(1);
        fixture.refresh.disabledProperty().addListener((observable, oldValue, disabled) -> {
            if (!disabled) {
                done.countDown();
            }
        });
        fixture.page.refresh();
        return done;
    }

    private static <T> T onFx(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static final class Fixture {
        private final MutableSessions sessions = new MutableSessions();
        private final List<IncidentId> opened = new ArrayList<>();
        private final ResponderPage page;
        private final Button refresh;
        private final Button open;
        private final VBox feedback;
        private final TableView<IncidentRowModel> eligible;
        private final TableView<IncidentRowModel> assigned;

        private Fixture(Supplier<ApplicationResult<ResponderDashboardModel>> load) {
            page = new ResponderPage(sessions, load, opened::add, () -> { });
            VBox content = (VBox) ((ScrollPane) page.getCenter()).getContent();
            FlowPane actions = (FlowPane) content.getChildren().get(2);
            refresh = (Button) actions.getChildren().get(1);
            open = (Button) actions.getChildren().get(2);
            feedback = (VBox) content.getChildren().get(3);
            eligible = tableIn((VBox) content.getChildren().get(4));
            assigned = tableIn((VBox) content.getChildren().get(5));
        }

        @SuppressWarnings("unchecked")
        private TableView<IncidentRowModel> tableIn(VBox panel) {
            IncidentTable component = (IncidentTable) ((VBox) panel.getChildren().get(1)).getChildren().getFirst();
            StackPane state = (StackPane) component.getChildren().get(1);
            return (TableView<IncidentRowModel>) state.getChildren().getFirst();
        }
    }
}

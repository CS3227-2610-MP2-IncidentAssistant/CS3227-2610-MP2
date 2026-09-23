package com.company.incidentdesk.ui.shared.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.application.presentation.IncidentActionModel;
import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.presentation.SloSummaryModel;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.persistence.AssignmentState;
import com.company.incidentdesk.persistence.IncidentSearchCriteria;
import com.company.incidentdesk.persistence.IncidentSloState;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

class IncidentTableTest {
    private static final IncidentRowModel FIRST = row("10000000-0000-0000-0000-000000000001", "First");
    private static final IncidentRowModel SECOND = row("20000000-0000-0000-0000-000000000002", "Second");

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
    void configuresDistinctColumnsForEveryRole() throws Exception {
        runOnJavaFx(() -> {
            IncidentTable reporter = new IncidentTable(IncidentTableConfiguration.reporter());
            IncidentTable responder = new IncidentTable(IncidentTableConfiguration.responder());
            IncidentTable admin = new IncidentTable(IncidentTableConfiguration.administrator());

            assertEquals(List.of("Reference", "Incident", "Category", "Status", "Submitted"),
                    headings(reporter));
            assertTrue(!IncidentTableConfiguration.reporter().sloFilterVisible());
            assertEquals(List.of("Reference", "Incident", "Category", "Status", "Reporter", "Queue entered", "SLO"),
                    headings(responder));
            assertTrue(IncidentTableConfiguration.responder().sloFilterVisible());
            assertEquals(List.of("Reference", "Incident", "Category", "Status", "Submitted", "Reporter", "Assignee", "SLO"),
                    headings(admin));
        });
    }

    @Test
    void rendersLoadingEmptyErrorAndContentStates() throws Exception {
        runOnJavaFx(() -> {
            IncidentTable table = new IncidentTable(IncidentTableConfiguration.reporter());
            assertTrue(table.tableView().getPlaceholder().getStyleClass().contains("loading-state"));
            assertTrue(table.tableView().getPlaceholder().getStyleClass().contains("incident-table-state"));
            assertEquals(Pos.CENTER, ((VBox) table.tableView().getPlaceholder()).getAlignment());

            table.setState(IncidentTableState.loaded(List.of()));
            assertTrue(table.tableView().getPlaceholder().getStyleClass().contains("empty-state"));

            table.setState(IncidentTableState.error("Storage unavailable"));
            assertTrue(table.tableView().getPlaceholder().getStyleClass().contains("error-banner"));

            table.setState(IncidentTableState.loaded(List.of(FIRST)));
            assertSame(table.tableView(), table.statePane().getChildren().getFirst());
            assertEquals(List.of(FIRST), table.tableView().getItems());
        });
    }

    @Test
    void filterChangesRequestRowsAndShowLoadingState() throws Exception {
        runOnJavaFx(() -> {
            IncidentTable table = new IncidentTable(IncidentTableConfiguration.reporter());
            AtomicReference<IncidentSearchCriteria> requested = new AtomicReference<>();
            table.setOnRefresh(requested::set);
            table.setState(IncidentTableState.loaded(List.of(FIRST)));

            FlowPane filterControls = (FlowPane) table.filterBar().getChildren().get(1);
            @SuppressWarnings("unchecked")
            ComboBox<AssignmentState> assignment = (ComboBox<AssignmentState>)
                    ((VBox) filterControls.getChildren().get(2)).getChildren().get(1);
            assignment.setValue(AssignmentState.ASSIGNED);

            assertEquals(table.criteria(), requested.get());
            assertTrue(table.tableView().getPlaceholder().getStyleClass().contains("loading-state"));
        });
    }

    @Test
    void preservesValidSelectionAcrossRefreshAndOpensItFromKeyboard() throws Exception {
        runOnJavaFx(() -> {
            IncidentTable table = new IncidentTable(IncidentTableConfiguration.administrator());
            table.setState(IncidentTableState.loaded(List.of(FIRST, SECOND)));
            table.tableView().getSelectionModel().select(SECOND);
            table.setState(IncidentTableState.loading());
            table.setState(IncidentTableState.loaded(List.of(SECOND, FIRST)));
            assertSame(SECOND, table.tableView().getSelectionModel().getSelectedItem());

            AtomicReference<IncidentRowModel> opened = new AtomicReference<>();
            table.setOnOpenDetail(opened::set);
            table.tableView().fireEvent(new KeyEvent(
                    KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false));
            assertSame(SECOND, opened.get());
        });
    }

    @Test
    void clearsSelectionWhenSelectedIncidentDisappears() throws Exception {
        runOnJavaFx(() -> {
            IncidentTable table = new IncidentTable(IncidentTableConfiguration.responder());
            table.setState(IncidentTableState.loaded(List.of(FIRST, SECOND)));
            table.tableView().getSelectionModel().select(FIRST);

            table.setState(IncidentTableState.loaded(List.of(SECOND)));

            assertEquals(null, table.tableView().getSelectionModel().getSelectedItem());
        });
    }

    @Test
    void showcaseStoreAppliesRoleVisibilityAndEveryAdvancedFilterGroup() {
        ComponentShowcasePage.DemoIncidentStore store = new ComponentShowcasePage.DemoIncidentStore();
        assertEquals(2, store.query(ComponentShowcasePage.DemoRole.REPORTER, IncidentSearchCriteria.defaults()).size());
        assertEquals(4, store.query(ComponentShowcasePage.DemoRole.RESPONDER, IncidentSearchCriteria.defaults()).size());
        assertEquals(6, store.query(ComponentShowcasePage.DemoRole.ADMINISTRATOR, IncidentSearchCriteria.defaults()).size());

        IncidentFilterBar.AccountOption taylor = store.reporters().get(2);
        IncidentSearchCriteria criteria = new IncidentSearchCriteria(
                "drive", Set.of(), Set.of(), AssignmentState.UNASSIGNED,
                Optional.of(taylor.id()), Optional.empty(),
                Optional.of(Instant.parse("2026-09-19T00:00:00Z")),
                Optional.of(Instant.parse("2026-09-20T00:00:00Z")),
                Set.of(IncidentSloState.OVERDUE), IncidentSearchCriteria.defaults().sort());

        assertTrue(store.query(ComponentShowcasePage.DemoRole.ADMINISTRATOR, criteria).isEmpty(),
                "Anonymous incidents must not match reporter identity filters");
        IncidentSearchCriteria withoutIdentity = new IncidentSearchCriteria(
                criteria.text(), criteria.categories(), criteria.statuses(), criteria.assignmentState(),
                Optional.empty(), Optional.empty(), criteria.createdFrom(), criteria.createdThrough(),
                criteria.sloStates(), criteria.sort());
        assertEquals(List.of("Shared drive unavailable"),
                store.query(ComponentShowcasePage.DemoRole.ADMINISTRATOR, withoutIdentity)
                        .stream().map(IncidentRowModel::title).toList());
    }

    private static List<String> headings(IncidentTable table) {
        return table.tableView().getColumns().stream().map(column -> column.getText()).toList();
    }

    private static IncidentRowModel row(String id, String title) {
        return new IncidentRowModel(
                new IncidentId(UUID.fromString(id)), title, "IT", "Submitted",
                "Visible reporter", "Unassigned", "23 Sep 2026, 09:40",
                "23 Sep 2026, 09:40", new SloSummaryModel("Healthy", .4, false),
                false, 0, new IncidentActionModel(
                        false, false, false, false, false, false, false, false, false));
    }

    private static void runOnJavaFx(Runnable assertions) throws Exception {
        FutureTask<Void> task = new FutureTask<>(assertions, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}

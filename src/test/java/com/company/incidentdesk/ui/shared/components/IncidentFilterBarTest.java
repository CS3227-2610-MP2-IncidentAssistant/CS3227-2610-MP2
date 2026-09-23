package com.company.incidentdesk.ui.shared.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.persistence.AssignmentState;
import com.company.incidentdesk.persistence.IncidentSearchCriteria;
import com.company.incidentdesk.persistence.IncidentSloState;
import com.company.incidentdesk.persistence.IncidentSort;
import com.company.incidentdesk.persistence.IncidentSortField;
import com.company.incidentdesk.persistence.SortDirection;

import javafx.application.Platform;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

class IncidentFilterBarTest {
    private static final AccountId REPORTER = new AccountId(
            UUID.fromString("f24a7d0a-7c48-4703-b9d7-a39c933149a1"));

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
    void restoresCriteriaAcrossNavigation() throws Exception {
        runOnJavaFx(() -> {
            IncidentFilterBar bar = new IncidentFilterBar();
            bar.setIdentityOptions(
                    List.of(new IncidentFilterBar.AccountOption(REPORTER, "Visible reporter")), List.of());
            IncidentSearchCriteria criteria = new IncidentSearchCriteria(
                    " printer ", Set.of(IncidentCategory.IT), Set.of(IncidentStatus.ASSIGNED),
                    AssignmentState.ASSIGNED, Optional.of(REPORTER), Optional.empty(),
                    Optional.of(Instant.parse("2026-09-01T00:00:00Z")),
                    Optional.of(Instant.parse("2026-09-30T23:59:59Z")),
                    Set.of(IncidentSloState.OVERDUE),
                    new IncidentSort(IncidentSortField.TITLE, SortDirection.ASCENDING));

            bar.setCriteria(criteria);
            IncidentSearchCriteria restored = bar.criteria();

            assertEquals("printer", restored.text());
            assertEquals(criteria.categories(), restored.categories());
            assertEquals(criteria.statuses(), restored.statuses());
            assertEquals(criteria.assignmentState(), restored.assignmentState());
            assertEquals(criteria.reporterId(), restored.reporterId());
            assertEquals(criteria.sloStates(), restored.sloStates());
            assertEquals(criteria.sort(), restored.sort());
        });
    }

    @Test
    void rendersMultiValueFiltersAsDropdowns() throws Exception {
        runOnJavaFx(() -> {
            IncidentFilterBar bar = new IncidentFilterBar();
            FlowPane filters = (FlowPane) bar.getChildren().get(1);

            assertTrue(((VBox) filters.getChildren().get(0))
                    .getChildren().get(1) instanceof MenuButton);
            assertTrue(((VBox) filters.getChildren().get(1))
                    .getChildren().get(1) instanceof MenuButton);
            assertTrue(((VBox) filters.getChildren().get(7))
                    .getChildren().get(1) instanceof MenuButton);
        });
    }

    @Test
    void givesFiltersAConsistentWidthAndKeepsMultiSelectMenusOpen() throws Exception {
        runOnJavaFx(() -> {
            IncidentFilterBar bar = new IncidentFilterBar();
            FlowPane filters = (FlowPane) bar.getChildren().get(1);

            filters.getChildren().forEach(node -> {
                VBox field = (VBox) node;
                assertEquals(IncidentFilterBar.FILTER_WIDTH, field.getPrefWidth());
                assertEquals(IncidentFilterBar.FILTER_WIDTH,
                        ((Control) field.getChildren().get(1)).getPrefWidth());
            });

            MenuButton categories = (MenuButton) ((VBox) filters.getChildren().get(0)).getChildren().get(1);
            assertTrue(categories.getItems().stream()
                    .map(CustomMenuItem.class::cast)
                    .noneMatch(CustomMenuItem::isHideOnClick));
        });
    }

    @Test
    void togglesASelectionFromTheWholeCheckboxFreeMenuRow() throws Exception {
        runOnJavaFx(() -> {
            IncidentFilterBar bar = new IncidentFilterBar();
            FlowPane filters = (FlowPane) bar.getChildren().get(1);
            MenuButton categories = (MenuButton) ((VBox) filters.getChildren().get(0)).getChildren().get(1);
            CustomMenuItem firstItem = (CustomMenuItem) categories.getItems().getFirst();

            HBox row = (HBox) firstItem.getContent();
            assertTrue(row.getChildren().stream().noneMatch(javafx.scene.control.CheckBox.class::isInstance));
            assertEquals(IncidentFilterBar.MENU_ROW_WIDTH, row.getMinWidth());
            assertEquals(IncidentFilterBar.MENU_ROW_WIDTH, row.getPrefWidth());
            assertEquals(IncidentFilterBar.MENU_ROW_WIDTH, row.getMaxWidth());
            firstItem.fire();

            assertTrue(bar.criteria().categories().contains(IncidentCategory.values()[0]));
            assertTrue(((Label) row.getChildren().get(1)).isVisible());
        });
    }

    private static void runOnJavaFx(Runnable assertions) throws Exception {
        FutureTask<Void> task = new FutureTask<>(assertions, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}

package com.company.incidentdesk.ui.shared.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.TextField;

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

    @Test
    void appliesNonTextFiltersImmediatelyAndResetRestoresDefaults() throws Exception {
        runOnJavaFx(() -> {
            IncidentFilterBar bar = new IncidentFilterBar();
            AtomicReference<IncidentSearchCriteria> requested = new AtomicReference<>();
            AtomicInteger requestCount = new AtomicInteger();
            bar.setOnSearch(criteria -> {
                requested.set(criteria);
                requestCount.incrementAndGet();
            });
            FlowPane filters = (FlowPane) bar.getChildren().get(1);
            @SuppressWarnings("unchecked")
            ComboBox<AssignmentState> assignment = (ComboBox<AssignmentState>)
                    ((VBox) filters.getChildren().get(2)).getChildren().get(1);

            assignment.setValue(AssignmentState.ASSIGNED);
            assertEquals(AssignmentState.ASSIGNED, requested.get().assignmentState());

            HBox query = (HBox) bar.getChildren().getFirst();
            assertEquals(2, query.getChildren().size());
            Button reset = (Button) query.getChildren().get(1);
            assertEquals("Reset", reset.getText());
            int requestsBeforeReset = requestCount.get();
            reset.fire();

            assertEquals(IncidentSearchCriteria.defaults(), requested.get());
            assertEquals(requestsBeforeReset + 1, requestCount.get());
        });
    }

    @Test
    void debouncesSearchTypingForTwoHundredMilliseconds() throws Exception {
        IncidentFilterBar bar = createOnJavaFx(IncidentFilterBar::new);
        CountDownLatch searched = new CountDownLatch(1);
        AtomicReference<IncidentSearchCriteria> requested = new AtomicReference<>();
        runOnJavaFx(() -> bar.setOnSearch(criteria -> {
            requested.set(criteria);
            searched.countDown();
        }));
        runOnJavaFx(() -> {
            TextField search = (TextField) ((HBox) bar.getChildren().getFirst()).getChildren().getFirst();
            search.setText("printer");
        });

        assertFalse(searched.await(100, TimeUnit.MILLISECONDS));
        assertTrue(searched.await(2, TimeUnit.SECONDS));
        assertEquals("printer", requested.get().text());
    }

    @Test
    void identityDropdownsIncludeExplicitEmptyFilterOptions() throws Exception {
        runOnJavaFx(() -> {
            IncidentFilterBar bar = new IncidentFilterBar();
            bar.setIdentityOptions(
                    List.of(new IncidentFilterBar.AccountOption(REPORTER, "Visible reporter")),
                    List.of(new IncidentFilterBar.AccountOption(REPORTER, "Visible responder")));
            FlowPane filters = (FlowPane) bar.getChildren().get(1);
            @SuppressWarnings("unchecked")
            ComboBox<IncidentFilterBar.AccountOption> reporters =
                    (ComboBox<IncidentFilterBar.AccountOption>)
                            ((VBox) filters.getChildren().get(3)).getChildren().get(1);
            @SuppressWarnings("unchecked")
            ComboBox<IncidentFilterBar.AccountOption> responders =
                    (ComboBox<IncidentFilterBar.AccountOption>)
                            ((VBox) filters.getChildren().get(4)).getChildren().get(1);

            assertEquals("Any reporter", reporters.getItems().getFirst().displayName());
            assertEquals("Any responder", responders.getItems().getFirst().displayName());
            reporters.setValue(reporters.getItems().getFirst());
            responders.setValue(responders.getItems().getFirst());
            assertTrue(bar.criteria().reporterId().isEmpty());
            assertTrue(bar.criteria().responderId().isEmpty());
        });
    }

    @Test
    void refreshingIdentityOptionsRetainsValidSelectionsWithoutTriggeringSearch() throws Exception {
        runOnJavaFx(() -> {
            IncidentFilterBar bar = new IncidentFilterBar();
            AtomicInteger searches = new AtomicInteger();
            IncidentFilterBar.AccountOption reporter = new IncidentFilterBar.AccountOption(REPORTER, "Reporter");
            bar.setIdentityOptions(List.of(reporter), List.of());
            bar.setCriteria(new IncidentSearchCriteria("", Set.of(), Set.of(), AssignmentState.ANY,
                    Optional.of(REPORTER), Optional.empty(), Optional.empty(), Optional.empty(), Set.of(),
                    new IncidentSort(IncidentSortField.CREATED_AT, SortDirection.DESCENDING)));
            bar.setOnSearch(criteria -> searches.incrementAndGet());

            bar.setIdentityOptions(List.of(reporter), List.of());

            assertEquals(Optional.of(REPORTER), bar.criteria().reporterId());
            assertEquals(0, searches.get());
        });
    }

    private static <T> T createOnJavaFx(java.util.function.Supplier<T> supplier) throws Exception {
        FutureTask<T> task = new FutureTask<>(supplier::get);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void runOnJavaFx(Runnable assertions) throws Exception {
        FutureTask<Void> task = new FutureTask<>(assertions, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}

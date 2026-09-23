package com.company.incidentdesk.ui.shared.components;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.presentation.SloSummaryModel;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.persistence.IncidentSearchCriteria;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.text.TextAlignment;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Role-configurable incident list that renders only authorized presentation rows. */
public final class IncidentTable extends VBox {
    private final IncidentFilterBar filters = new IncidentFilterBar();
    private final TableView<IncidentRowModel> table = new TableView<>();
    private final StackPane statePane = new StackPane();
    private Consumer<IncidentSearchCriteria> onRefresh = ignored -> { };
    private Consumer<IncidentRowModel> onOpenDetail = ignored -> { };
    private IncidentId retainedSelection;

    public IncidentTable(IncidentTableConfiguration configuration) {
        super(12);
        Objects.requireNonNull(configuration, "configuration");
        getStyleClass().add("incident-table");
        filters.setIdentityFiltersVisible(configuration.identityFiltersVisible());
        filters.setSloFilterVisible(configuration.sloFilterVisible());
        filters.setOnSearch(this::refresh);

        table.setAccessibleText("Incidents");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(52);
        table.setPlaceholder(new Label(""));
        table.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (selected != null) {
                retainedSelection = selected.id();
            }
        });
        table.getColumns().setAll(configuration.columns().stream().map(this::column).toList());
        table.setRowFactory(ignored -> interactiveRow());
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                openSelected();
                event.consume();
            }
        });
        VBox.setVgrow(statePane, Priority.ALWAYS);
        statePane.getChildren().setAll(table);
        getChildren().addAll(filters, statePane);
        setState(IncidentTableState.loading());
    }

    /** Supplies privacy-safe identity choices for role configurations that display them. */
    public void setIdentityOptions(
            java.util.List<IncidentFilterBar.AccountOption> reporters,
            java.util.List<IncidentFilterBar.AccountOption> responders) {
        filters.setIdentityOptions(reporters, responders);
    }

    public void setOnRefresh(Consumer<IncidentSearchCriteria> handler) {
        onRefresh = Objects.requireNonNull(handler, "handler");
    }

    public void setOnOpenDetail(Consumer<IncidentRowModel> handler) {
        onOpenDetail = Objects.requireNonNull(handler, "handler");
    }

    /** Renders a complete load result and retains the selected incident when it is still present. */
    public void setState(IncidentTableState state) {
        Objects.requireNonNull(state, "state");
        IncidentId selectedId = Optional.ofNullable(table.getSelectionModel().getSelectedItem())
                .map(IncidentRowModel::id).orElse(retainedSelection);
        if (state.status() == IncidentTableState.Status.CONTENT) {
            table.getItems().setAll(state.rows());
            table.setPlaceholder(new Label(""));
            if (selectedId != null) {
                Optional<IncidentRowModel> matchingRow = state.rows().stream()
                        .filter(row -> row.id().equals(selectedId)).findFirst();
                matchingRow.ifPresentOrElse(
                        table.getSelectionModel()::select,
                        () -> retainedSelection = null);
            }
            return;
        }
        table.getItems().clear();
        table.setPlaceholder(feedback(state));
    }

    public IncidentSearchCriteria criteria() {
        return filters.criteria();
    }

    public void setCriteria(IncidentSearchCriteria criteria) {
        filters.setCriteria(criteria);
    }

    TableView<IncidentRowModel> tableView() {
        return table;
    }

    StackPane statePane() {
        return statePane;
    }

    IncidentFilterBar filterBar() {
        return filters;
    }

    private void refresh(IncidentSearchCriteria criteria) {
        setState(IncidentTableState.loading());
        onRefresh.accept(criteria);
    }

    private Node feedback(IncidentTableState state) {
        return switch (state.status()) {
            case LOADING -> tableFeedback(
                    "Loading incidents", "Reading incidents from local storage.", FeedbackType.LOADING);
            case EMPTY -> tableFeedback(
                    "No incidents found", "Adjust the filters or refresh the list.", FeedbackType.EMPTY);
            case ERROR -> {
                VBox error = tableFeedback(
                        "Incidents could not be loaded", state.errorDetail(), FeedbackType.ERROR);
                Button retry = UiComponents.action("Try again", ActionStyle.SECONDARY);
                retry.setOnAction(event -> refresh(filters.criteria()));
                VBox.setMargin(retry, new Insets(10, 0, 0, 0));
                error.getChildren().add(retry);
                yield error;
            }
            case CONTENT -> throw new IllegalArgumentException("Content state uses the table");
        };
    }

    private VBox tableFeedback(String title, String detail, FeedbackType type) {
        VBox feedback = UiComponents.feedback(title, detail, type);
        feedback.getStyleClass().add("incident-table-state");
        feedback.setAlignment(Pos.CENTER);
        feedback.getChildren().stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .forEach(label -> {
                    label.setMaxWidth(Double.MAX_VALUE);
                    label.setAlignment(Pos.CENTER);
                    label.setTextAlignment(TextAlignment.CENTER);
                });
        return feedback;
    }

    private TableRow<IncidentRowModel> interactiveRow() {
        TableRow<IncidentRowModel> row = new TableRow<>();
        row.setOnMouseClicked(event -> {
            if (!row.isEmpty() && event.getClickCount() == 2) {
                table.getSelectionModel().select(row.getItem());
                openSelected();
            }
        });
        return row;
    }

    private void openSelected() {
        IncidentRowModel selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            onOpenDetail.accept(selected);
        }
    }

    private TableColumn<IncidentRowModel, ?> column(IncidentTableColumn column) {
        return switch (column) {
            case REFERENCE -> textColumn("Reference", row -> shortReference(row.id()));
            case TITLE -> textColumn("Incident", IncidentRowModel::title);
            case CATEGORY -> textColumn("Category", IncidentRowModel::categoryLabel);
            case STATUS -> badgeColumn("Status", IncidentRowModel::statusLabel, this::statusTone);
            case SUBMITTED -> textColumn("Submitted", IncidentRowModel::createdAt);
            case REPORTER -> textColumn("Reporter", IncidentRowModel::reporterLabel);
            case ASSIGNEE -> textColumn("Assignee", IncidentRowModel::assigneeLabel);
            case QUEUE_ENTERED -> textColumn("Queue entered", IncidentRowModel::queueEnteredAt);
            case SLO -> sloColumn();
        };
    }

    private TableColumn<IncidentRowModel, String> textColumn(
            String title,
            java.util.function.Function<IncidentRowModel, String> value) {
        TableColumn<IncidentRowModel, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private TableColumn<IncidentRowModel, String> badgeColumn(
            String title,
            java.util.function.Function<IncidentRowModel, String> value,
            java.util.function.Function<String, SemanticTone> tone) {
        TableColumn<IncidentRowModel, String> column = textColumn(title, value);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : UiComponents.badge(item, tone.apply(item)));
            }
        });
        return column;
    }

    private TableColumn<IncidentRowModel, SloSummaryModel> sloColumn() {
        TableColumn<IncidentRowModel, SloSummaryModel> column = new TableColumn<>("SLO");
        column.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cell.getValue().slo()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(SloSummaryModel item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                SemanticTone tone = item != null && item.overdue() ? SemanticTone.DANGER : SemanticTone.INFO;
                setGraphic(empty || item == null ? null : UiComponents.badge(item.label(), tone));
            }
        });
        return column;
    }

    private SemanticTone statusTone(String status) {
        return switch (status) {
            case "Resolved" -> SemanticTone.SUCCESS;
            case "Withdrawn" -> SemanticTone.NEUTRAL;
            case "Assigned" -> SemanticTone.WARNING;
            default -> SemanticTone.INFO;
        };
    }

    private static String shortReference(IncidentId id) {
        return "INC-" + id.value().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }
}

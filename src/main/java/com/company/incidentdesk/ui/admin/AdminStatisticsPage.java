package com.company.incidentdesk.ui.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.company.incidentdesk.application.statistics.ResponderStatisticsView;
import com.company.incidentdesk.application.statistics.StatisticsGateway;
import com.company.incidentdesk.application.statistics.StatisticsResult;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.slo.SloEvaluation;
import com.company.incidentdesk.domain.statistics.StatisticsSummary;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Administrator dashboard for privacy-safe, chart/table-neutral operational statistics. */
public final class AdminStatisticsPage extends BorderPane {
    private final AdminStatisticsPresenter presenter;
    private final VBox body = new VBox(16);
    private final ComboBox<IncidentCategory> category = new ComboBox<>();
    private final DatePicker periodFrom = new DatePicker();
    private final DatePicker periodThrough = new DatePicker();

    public AdminStatisticsPage(StatisticsGateway statistics) {
        presenter = new AdminStatisticsPresenter(Objects.requireNonNull(statistics, "statistics"));
        configureFilters();
        Label title = new Label("Operational statistics");
        title.getStyleClass().add("page-title");
        Label explanation = new Label(
                "Resolutions are attributed to the responder assigned when each resolution occurred. "
                        + "Time to claim reflects queue performance and is not attributed to a responder.");
        explanation.setWrapText(true);
        body.getChildren().addAll(title, explanation);
        body.setPadding(new Insets(24));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        setCenter(scroll);
        presenter.load();
        render();
    }

    private void render() {
        body.getChildren().remove(2, body.getChildren().size());
        switch (presenter.state()) {
        case LOADING -> body.getChildren().add(feedback(
                "Loading statistics", "Reading operational statistics.", FeedbackType.LOADING));
        case UNAVAILABLE -> body.getChildren().add(feedback(
                "Statistics unavailable", "Sign in as an administrator and try again.", FeedbackType.ERROR));
        case STORAGE_ERROR -> body.getChildren().addAll(feedback(
                "Statistics could not be read",
                "Check application storage and try again.", FeedbackType.ERROR), retryButton());
        case READY -> renderReady();
        }
    }

    private void renderReady() {
        StatisticsResult result = presenter.result();
        body.getChildren().addAll(filterPanel(), summaryPanel(result), responderPanel(result));
    }

    private Node filterPanel() {
        return UiComponents.panel("Filters", new FlowPane(14, 14,
                UiComponents.field("Category", category),
                UiComponents.field("Period from", periodFrom),
                UiComponents.field("Period through", periodThrough)));
    }

    private Node summaryPanel(StatisticsResult result) {
        if (result.companySummary().isEmpty()) {
            return UiComponents.panel("Company summary",
                    new Label("No incidents match the selected filters."));
        }
        StatisticsSummary summary = result.companySummary().orElseThrow();
        SloEvaluation aggregate = summary.aggregate();
        int administratorResolved = summary.administratorResolvedCount();
        FlowPane cards = new FlowPane(14, 14,
                UiComponents.metricCard("Incidents resolved",
                        Integer.toString(aggregate.resolvedIncidentCount()),
                        "Across the selected category and period"),
                UiComponents.metricCard("Average time to claim",
                        aggregate.averageTimeToClaim().map(SloTargetFormat::duration).orElse("No data"),
                        "Queue performance; not attributed to a responder"),
                UiComponents.metricCard("Average time in progress",
                        aggregate.averageTimeInProgress().map(SloTargetFormat::duration).orElse("No data"),
                        "From first assignment to resolution"),
                UiComponents.metricCard("Reopen rate",
                        aggregate.reopenRate().map(SloTargetFormat::percent).orElse("No data"),
                        aggregate.reopenedIncidentCount() + " of " + aggregate.resolvedIncidentCount()
                                + " resolved incidents reopened"),
                UiComponents.metricCard("Administrator-resolved",
                        Integer.toString(administratorResolved),
                        "Resolved by an administrator rather than the assigned responder"));
        return UiComponents.panel("Company summary", cards);
    }

    private Node responderPanel(StatisticsResult result) {
        TableView<ResponderStatisticsView> table = new TableView<>();
        table.setAccessibleText("Responder statistics breakdown");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("Responder", ResponderStatisticsView::displayName));
        table.getColumns().add(column("Incidents resolved",
                view -> Integer.toString(view.statistics().resolvedCycleCount())));
        table.getColumns().add(column("Average time in progress", view -> view.statistics().averageTimeInProgress()
                .map(SloTargetFormat::duration).orElse("No data")));
        table.getColumns().add(column("Reopen rate", view -> view.statistics().reopenRate()
                .map(SloTargetFormat::percent).orElse("No data")));
        table.getColumns().add(column("Administrator-resolved",
                view -> Integer.toString(view.statistics().administratorResolvedCount())));
        table.getItems().setAll(result.responders());
        table.setPlaceholder(new Label("No responder has a resolution in the selected scope."));
        return UiComponents.panel("Responder breakdown", table);
    }

    private TableColumn<ResponderStatisticsView, String> column(
            String title, Function<ResponderStatisticsView, String> value) {
        TableColumn<ResponderStatisticsView, String> tableColumn = new TableColumn<>(title);
        tableColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return tableColumn;
    }

    private void configureFilters() {
        category.setItems(FXCollections.observableArrayList(
                withAllCategoriesOption(List.of(IncidentCategory.values()))));
        category.setConverter(new StringConverter<>() {
            @Override
            public String toString(IncidentCategory value) {
                return value == null ? "All categories" : value.displayName();
            }

            @Override
            public IncidentCategory fromString(String value) {
                throw new UnsupportedOperationException("Category selection is read-only");
            }
        });
        category.setAccessibleText("Category filter");
        category.setOnAction(event -> {
            presenter.setCategory(Optional.ofNullable(category.getValue()));
            render();
        });
        periodFrom.setAccessibleText("Period start");
        periodFrom.valueProperty().addListener((observable, previous, current) -> {
            presenter.setPeriodFrom(Optional.ofNullable(current));
            render();
        });
        periodThrough.setAccessibleText("Period end");
        periodThrough.valueProperty().addListener((observable, previous, current) -> {
            presenter.setPeriodThrough(Optional.ofNullable(current));
            render();
        });
    }

    private static List<IncidentCategory> withAllCategoriesOption(List<IncidentCategory> categories) {
        List<IncidentCategory> withAll = new ArrayList<>();
        withAll.add(null);
        withAll.addAll(categories);
        return withAll;
    }

    private Button retryButton() {
        Button retry = UiComponents.action("Try again", ActionStyle.SECONDARY);
        retry.setOnAction(event -> {
            presenter.load();
            render();
        });
        return retry;
    }

    private VBox feedback(String title, String detail, FeedbackType type) {
        return UiComponents.feedback(title, detail, type);
    }
}

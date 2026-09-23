package com.company.incidentdesk.ui.shared.components;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Interactive catalogue of the visual primitives needed by the product. */
public final class ComponentShowcasePage extends BorderPane {
    private static final double PAGE_PADDING = 28;
    private static final double SECTION_SPACING = 18;

    public ComponentShowcasePage(Runnable onBack) {
        setTop(createHeader(onBack));

        VBox catalogue = new VBox(
                SECTION_SPACING,
                createActionsPanel(),
                createBadgesPanel(),
                createMetricsPanel(),
                createFormsPanel(),
                createDataPanel(),
                createFeedbackPanel(),
                createCollaborationPanel(),
                createProgressPanel());
        catalogue.setPadding(new Insets(PAGE_PADDING));
        catalogue.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(catalogue);
        scrollPane.setFitToWidth(true);
        setCenter(scrollPane);
        getStyleClass().addAll("content-pane", "component-showcase");
    }

    private Node createHeader(Runnable onBack) {
        Label title = new Label("UI component showcase");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Shared, accessible primitives for reporter, responder, and administrator pages");
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);

        Button back = UiComponents.action("Back to role selection", ActionStyle.SECONDARY);
        back.setOnAction(event -> onBack.run());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(16, new VBox(4, title, subtitle), spacer, back);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(24, PAGE_PADDING, 12, PAGE_PADDING));
        return header;
    }

    private Node createActionsPanel() {
        FlowPane actions = new FlowPane(10, 10);
        actions.getChildren().addAll(
                UiComponents.action("Create incident", ActionStyle.PRIMARY),
                UiComponents.action("Secondary action", ActionStyle.SECONDARY),
                UiComponents.action("Delete account", ActionStyle.DANGER),
                UiComponents.action("Low emphasis", ActionStyle.GHOST),
                UiComponents.action("Edit", ActionStyle.SMALL));

        Button disabled = UiComponents.action("Unavailable", ActionStyle.SECONDARY);
        disabled.setDisable(true);
        Button confirm = UiComponents.action("Open confirmation", ActionStyle.SECONDARY);
        confirm.setOnAction(event -> showConfirmation());
        actions.getChildren().addAll(disabled, confirm);
        return UiComponents.panel("Buttons and confirmation dialog", actions);
    }

    private Node createBadgesPanel() {
        FlowPane badges = new FlowPane(10, 10);
        badges.getChildren().addAll(
                UiComponents.badge("Submitted", SemanticTone.INFO),
                UiComponents.badge("Resolved", SemanticTone.SUCCESS),
                UiComponents.badge("At risk", SemanticTone.WARNING),
                UiComponents.badge("High priority", SemanticTone.DANGER),
                UiComponents.badge("Administrator", SemanticTone.NEUTRAL),
                UiComponents.badge("IT", SemanticTone.INFO));
        return UiComponents.panel("Status, category, SLO, priority, and role badges", badges);
    }

    private Node createMetricsPanel() {
        GridPane metrics = new GridPane();
        metrics.setHgap(14);
        metrics.setVgap(14);
        for (int column = 0; column < 4; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(25);
            metrics.getColumnConstraints().add(constraints);
        }
        metrics.add(UiComponents.metricCard("Open incidents", "24", "Across all categories"), 0, 0);
        metrics.add(UiComponents.metricCard("Resolved this week", "18", "+12% from last week"), 1, 0);
        metrics.add(UiComponents.metricCard("Within SLO", "92%", "Healthy performance"), 2, 0);
        metrics.add(UiComponents.metricCard("Reopened", "3", "Requires review"), 3, 0);
        return UiComponents.panel("Metric cards", metrics);
    }

    private Node createFormsPanel() {
        TextField title = new TextField("Printer unavailable");
        title.setAccessibleText("Incident title");
        ComboBox<String> category = new ComboBox<>(
                FXCollections.observableArrayList("IT", "Human Relations", "Facilities"));
        category.setValue("IT");
        category.setAccessibleText("Incident category");
        DatePicker date = new DatePicker();
        date.setAccessibleText("Incident date");
        TextArea description = new TextArea("The shared office printer is not responding.");
        description.setPrefRowCount(3);
        description.setWrapText(true);
        description.setAccessibleText("Incident description");
        CheckBox anonymous = new CheckBox("Submit anonymously");

        TextField invalid = new TextField();
        invalid.setPromptText("Required title");
        ValidatedField invalidField = UiComponents.field("Validation example", invalid);
        invalidField.showError("Title is required.");

        GridPane form = new GridPane();
        form.setHgap(18);
        form.setVgap(14);
        form.add(UiComponents.field("Title", title), 0, 0);
        form.add(UiComponents.field("Category", category), 1, 0);
        form.add(UiComponents.field("Date", date), 0, 1);
        form.add(invalidField, 1, 1);
        form.add(UiComponents.field("Description", description), 0, 2, 2, 1);
        form.add(anonymous, 0, 3, 2, 1);
        return UiComponents.panel("Fields, forms, and validation", form);
    }

    private Node createDataPanel() {
        TableView<IncidentRow> table = new TableView<>();
        table.setAccessibleText("Sample incident list");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(48);
        table.setPrefHeight(190);
        table.setMinHeight(190);
        table.setMaxHeight(190);

        TableColumn<IncidentRow, String> title = new TableColumn<>("Incident");
        title.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().title()));
        TableColumn<IncidentRow, String> category = new TableColumn<>("Category");
        category.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().category()));
        TableColumn<IncidentRow, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().status()));
        TableColumn<IncidentRow, String> updated = new TableColumn<>("Updated");
        updated.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().updated()));
        table.getColumns().addAll(List.of(title, category, status, updated));
        table.getItems().addAll(List.of(
                new IncidentRow("Printer unavailable", "IT", "Submitted", "22 Sep 2026, 09:40"),
                new IncidentRow("Water leak", "Facilities", "Assigned", "22 Sep 2026, 08:15"),
                new IncidentRow("Access request", "Human Relations", "Resolved", "21 Sep 2026, 17:30")));

        IncidentFilterBar filters = new IncidentFilterBar();
        filters.setIdentityFiltersVisible(false);
        return UiComponents.panel("Search, filter, table, and list rows", filters, table);
    }

    private Node createFeedbackPanel() {
        VBox loading = UiComponents.feedback(
                "Loading incidents",
                "Shown while records are being read from local storage.",
                FeedbackType.LOADING);
        VBox empty = UiComponents.feedback(
                "No incidents found",
                "Shown after a successful query returns no records. Adjust the filters or create an incident.",
                FeedbackType.EMPTY);
        VBox error = UiComponents.feedback(
                "Incidents could not be loaded",
                "Shown when an operation fails. Check the data files and try again.",
                FeedbackType.ERROR);
        VBox success = UiComponents.feedback(
                "Incident created",
                "A short-lived confirmation shown after an action succeeds.",
                FeedbackType.SUCCESS);

        FlowPane states = new FlowPane(14, 14, loading, empty, error, success);
        return UiComponents.panel("Loading, empty, error, and success states", states);
    }

    private Node createCollaborationPanel() {
        VBox attachment = UiComponents.attachmentTile(
                "Image attachment",
                "office-printer.jpg · 1.4 MB");
        HBox timeline = UiComponents.timelineEvent("Assigned to Morgan Lee · "
                + UiComponents.localDateTimeFormatter().format(Instant.parse("2026-09-22T02:15:00Z")));
        Clock sampleClock = sampleCommentClock();
        VBox comments = new VBox(
                10,
                UiComponents.comment("AR", "Alex Rivera", "Reporter", sampleCommentInstant(2026, 9, 25, 8, 43), sampleClock, "The issue affects the whole third floor."),
                UiComponents.comment("ML", "Morgan Lee", "Responder", sampleCommentInstant(2026, 9, 24, 16, 14), sampleClock, "I have reproduced the printer fault."),
                UiComponents.comment("PS", "Priya Shah", "Administrator", sampleCommentInstant(2026, 9, 19, 12, 0), sampleClock, "The incident has been moved to the IT queue."),
                UiComponents.comment("JT", "Jordan Tan", "Responder", sampleCommentInstant(2026, 6, 24, 15, 0), sampleClock, "The replacement component has arrived."));
        return UiComponents.panel("Attachments, comments, and lifecycle timeline", attachment, comments, timeline);
    }

    private Node createProgressPanel() {
        SloProgressBar slo = UiComponents.sloProgress(
                0.72,
                "Seventy-two percent of the incident SLO elapsed");

        CategoryAxis categories = new CategoryAxis();
        NumberAxis totals = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(categories, totals);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCategoryGap(34);
        chart.setBarGap(18);
        chart.setPrefHeight(200);
        chart.setMaxWidth(560);
        chart.setAccessibleText("Resolved incidents by category");
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().addAll(List.of(
                new XYChart.Data<>("IT", 12),
                new XYChart.Data<>("Facilities", 7),
                new XYChart.Data<>("Human Relations", 4)));
        chart.getData().add(series);
        return UiComponents.panel("SLO progress and operational chart", new Label("SLO elapsed: 72%"), slo, chart);
    }

    private Instant sampleCommentInstant(int year, int month, int day, int hour, int minute) {
        ZoneId zone = ZoneId.systemDefault();
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant();
    }

    private Clock sampleCommentClock() {
        ZoneId zone = ZoneId.systemDefault();
        Instant reference = ZonedDateTime.of(2026, 9, 25, 18, 0, 0, 0, zone).toInstant();
        return Clock.fixed(reference, zone);
    }

    private void showConfirmation() {
        Alert dialog = new Alert(
                Alert.AlertType.CONFIRMATION,
                "This sample demonstrates a consequential action with an explicit outcome.",
                ButtonType.CANCEL,
                ButtonType.OK);
        dialog.setTitle("Confirm action");
        dialog.setHeaderText("Proceed with the sample action?");
        dialog.getDialogPane().setAccessibleText("Confirmation dialog for the sample action");
        dialog.showAndWait();
    }

    /** Read-only table data used by the showcase. */
    public record IncidentRow(String title, String category, String status, String updated) {
    }
}

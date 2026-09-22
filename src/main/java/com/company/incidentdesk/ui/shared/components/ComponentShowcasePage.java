package com.company.incidentdesk.ui.shared.components;

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
import javafx.scene.layout.StackPane;
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
        getStyleClass().add("content-pane");
    }

    private Node createHeader(Runnable onBack) {
        Label title = new Label("UI component showcase");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Shared, accessible primitives for reporter, responder, and administrator pages");
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);

        Button back = UiComponents.button("Back to role selection", "secondary");
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
                UiComponents.button("Create incident", "primary"),
                UiComponents.button("Secondary action", "secondary"),
                UiComponents.button("Delete account", "danger"),
                UiComponents.button("Low emphasis", "ghost"),
                UiComponents.button("Edit", "small"));

        Button disabled = UiComponents.button("Unavailable", "secondary");
        disabled.setDisable(true);
        Button confirm = UiComponents.button("Open confirmation", "secondary");
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
        metrics.add(metricCard("Open incidents", "24", "Across all categories"), 0, 0);
        metrics.add(metricCard("Resolved this week", "18", "+12% from last week"), 1, 0);
        metrics.add(metricCard("Within SLO", "92%", "Healthy performance"), 2, 0);
        metrics.add(metricCard("Reopened", "3", "Requires review"), 3, 0);
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
        invalid.setAccessibleText("Invalid incident title. Title is required.");
        invalid.getStyleClass().add("invalid");
        Label error = new Label("Title is required.");
        error.getStyleClass().add("field-error");

        GridPane form = new GridPane();
        form.setHgap(18);
        form.setVgap(14);
        addField(form, 0, "Title", title);
        addField(form, 1, "Category", category);
        addField(form, 2, "Date", date);
        addField(form, 3, "Validation example", new VBox(5, invalid, error));
        form.add(new VBox(7, new Label("Description"), description), 0, 2, 2, 1);
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

        TextField search = new TextField();
        search.setPromptText("Search incidents");
        search.setAccessibleText("Search sample incidents");
        ComboBox<String> filter = new ComboBox<>(FXCollections.observableArrayList("All statuses", "Submitted", "Assigned", "Resolved"));
        filter.setValue("All statuses");
        HBox controls = new HBox(10, search, filter);
        HBox.setHgrow(search, Priority.ALWAYS);
        return UiComponents.panel("Search, filter, table, and list rows", controls, table);
    }

    private Node createFeedbackPanel() {
        VBox loading = stateCard(
                "Loading incidents",
                "Shown while records are being read from local storage.",
                "loading-state");
        VBox empty = stateCard(
                "No incidents found",
                "Shown after a successful query returns no records. Adjust the filters or create an incident.",
                "empty-state");
        VBox error = stateCard(
                "Incidents could not be loaded",
                "Shown when an operation fails. Check the data files and try again.",
                "error-banner");
        VBox success = stateCard(
                "Incident created",
                "A short-lived confirmation shown after an action succeeds.",
                "toast");

        FlowPane states = new FlowPane(14, 14, loading, empty, error, success);
        return UiComponents.panel("Loading, empty, error, and success states", states);
    }

    private Node createCollaborationPanel() {
        VBox attachment = stateCard("Image attachment", "office-printer.jpg · 1.4 MB", "attachment-tile");
        Label marker = new Label();
        marker.getStyleClass().add("timeline-marker");
        Label event = new Label("Assigned to Morgan Lee · "
                + UiComponents.localDateTimeFormatter().format(Instant.parse("2026-09-22T02:15:00Z")));
        HBox timeline = new HBox(10, marker, event);
        timeline.setAlignment(Pos.CENTER_LEFT);
        VBox comments = new VBox(
                10,
                createComment("AR", "Alex Rivera", "Reporter", sampleCommentDate(2026, 9, 25, 8, 43), "The issue affects the whole third floor."),
                createComment("ML", "Morgan Lee", "Responder", sampleCommentDate(2026, 9, 24, 16, 14), "I have reproduced the printer fault."),
                createComment("PS", "Priya Shah", "Administrator", sampleCommentDate(2026, 9, 19, 12, 0), "The incident has been moved to the IT queue."),
                createComment("JT", "Jordan Tan", "Responder", sampleCommentDate(2026, 6, 24, 15, 0), "The replacement component has arrived."));
        return UiComponents.panel("Attachments, comments, and lifecycle timeline", attachment, comments, timeline);
    }

    private Node createProgressPanel() {
        StackPane slo = createSloProgress(0.72);

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

    private StackPane createSloProgress(double progress) {
        Region fill = new Region();
        fill.getStyleClass().add("slo-progress-fill");

        StackPane track = new StackPane(fill);
        track.getStyleClass().add("slo-progress-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setAccessibleText("Seventy-two percent of the incident SLO elapsed");
        fill.minWidthProperty().bind(track.widthProperty().multiply(progress));
        fill.prefWidthProperty().bind(track.widthProperty().multiply(progress));
        fill.maxWidthProperty().bind(track.widthProperty().multiply(progress));
        return track;
    }

    private VBox metricCard(String label, String value, String detail) {
        Label metricLabel = new Label(label);
        metricLabel.getStyleClass().add("metric-label");
        Label metricValue = new Label(value);
        metricValue.getStyleClass().add("metric-value");
        Label metricDetail = new Label(detail);
        metricDetail.getStyleClass().add("muted");
        VBox card = new VBox(6, metricLabel, metricValue, metricDetail);
        card.getStyleClass().add("metric-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void addField(GridPane form, int column, String labelText, Node field) {
        form.add(new VBox(7, new Label(labelText), field), column % 2, column / 2);
    }

    private VBox stateCard(String title, String detail, String styleClass) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        Label description = new Label(detail);
        description.setWrapText(true);
        VBox card = new VBox(7, heading, description);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPrefWidth(310);
        card.setMinWidth(310);
        card.setMaxWidth(310);
        card.setMinHeight(145);
        card.getStyleClass().add(styleClass);
        return card;
    }

    private HBox createComment(String initials, String name, String roleName, String sentAtText, String bodyText) {
        Label avatar = new Label(initials);
        avatar.getStyleClass().add("avatar");

        Label author = new Label(name);
        author.getStyleClass().add("comment-author");
        Label role = new Label("· " + roleName);
        role.getStyleClass().add("muted");
        HBox identity = new HBox(5, author, role);

        Label sentAt = new Label(sentAtText);
        sentAt.getStyleClass().addAll("muted", "comment-date");
        Label message = new Label(bodyText);
        message.setWrapText(true);

        VBox body = new VBox(4, identity, sentAt, message);
        body.getStyleClass().add("comment-body");
        HBox comment = new HBox(10, avatar, body);
        HBox.setHgrow(body, Priority.ALWAYS);
        return comment;
    }

    private String sampleCommentDate(int year, int month, int day, int hour, int minute) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime reference = ZonedDateTime.of(2026, 9, 25, 18, 0, 0, 0, zone);
        Instant timestamp = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant();
        return UiDateTimeFormatter.formatRelative(timestamp, reference);
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

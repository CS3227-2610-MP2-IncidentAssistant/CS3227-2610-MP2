package com.company.incidentdesk.ui.shared.components;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import com.company.incidentdesk.application.notification.Notification;
import com.company.incidentdesk.application.notification.NotificationId;
import com.company.incidentdesk.application.notification.NotificationInbox;
import com.company.incidentdesk.application.notification.NotificationType;
import com.company.incidentdesk.application.presentation.IncidentActionModel;
import com.company.incidentdesk.application.presentation.IncidentDisplayLabels;
import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.presentation.SloSummaryModel;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentStatus;
import com.company.incidentdesk.persistence.AssignmentState;
import com.company.incidentdesk.persistence.IncidentSearchCriteria;
import com.company.incidentdesk.persistence.IncidentSloState;
import com.company.incidentdesk.persistence.SortDirection;

/** Interactive catalogue of the visual primitives needed by the product. */
public final class ComponentShowcasePage extends BorderPane {
    private static final double PAGE_PADDING = 28;
    private static final double SECTION_SPACING = 18;
    private static final AccountId SHOWCASE_ACCOUNT_ID = new AccountId(new UUID(0, 1));

    private final NotificationInbox showcaseNotifications = new NotificationInbox();

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
                createNotificationPanel(),
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

        Button back = UiComponents.action("Back to sign in", ActionStyle.SECONDARY);
        back.setId("showcase-back");
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
        DemoIncidentStore store = new DemoIncidentStore();
        VBox tableHost = new VBox();
        Label selection = new Label("Double-click a row or press Enter to open its detail callback.");
        selection.getStyleClass().add("muted");

        ToggleGroup roles = new ToggleGroup();
        FlowPane roleToggle = new FlowPane(8, 8);
        for (DemoRole role : DemoRole.values()) {
            ToggleButton button = new ToggleButton(role.label);
            button.setToggleGroup(roles);
            button.setUserData(role);
            button.getStyleClass().add("role-view-toggle");
            roleToggle.getChildren().add(button);
        }
        roles.selectedToggleProperty().addListener((observable, previous, selected) -> {
            if (selected == null) {
                roles.selectToggle(previous);
                return;
            }
            showDemoTable((DemoRole) selected.getUserData(), store, tableHost, selection);
        });
        ((ToggleButton) roleToggle.getChildren().getFirst()).setSelected(true);

        FlowPane stateControls = new FlowPane(8, 8,
                stateButton("Show loading", () -> currentDemoTable(tableHost)
                        .setState(IncidentTableState.loading())),
                stateButton("Show empty", () -> currentDemoTable(tableHost)
                        .setState(IncidentTableState.loaded(List.of()))),
                stateButton("Show error", () -> currentDemoTable(tableHost)
                        .setState(IncidentTableState.error(
                                "Sample storage failure. Use Try again or Restore data."))),
                stateButton("Restore data", () -> {
                    IncidentTable table = currentDemoTable(tableHost);
                    DemoRole role = (DemoRole) roles.getSelectedToggle().getUserData();
                    table.setState(IncidentTableState.loaded(store.query(role, table.criteria())));
                }));
        return UiComponents.panel(
                "Reusable incident table", roleToggle, stateControls, selection, tableHost);
    }

    private Button stateButton(String label, Runnable action) {
        Button button = UiComponents.action(label, ActionStyle.SECONDARY);
        button.setOnAction(event -> action.run());
        return button;
    }

    private IncidentTable currentDemoTable(VBox tableHost) {
        return (IncidentTable) tableHost.getChildren().getFirst();
    }

    private void showDemoTable(
            DemoRole role,
            DemoIncidentStore store,
            VBox tableHost,
            Label selection) {
        IncidentTable table = new IncidentTable(role.configuration);
        table.setPrefHeight(430);
        table.setIdentityOptions(store.reporters(), store.responders());
        table.setOnOpenDetail(row -> selection.setText("Open detail: " + row.title()));
        table.setOnRefresh(criteria -> table.setState(IncidentTableState.loaded(store.query(role, criteria))));
        table.setState(IncidentTableState.loaded(store.query(role, table.criteria())));
        tableHost.getChildren().setAll(table);
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

    private Node createNotificationPanel() {
        NotificationCenter notificationCenter = new NotificationCenter(
                showcaseNotifications, SHOWCASE_ACCOUNT_ID);
        Button getNotification = UiComponents.action("Get notification", ActionStyle.PRIMARY);
        getNotification.setId("get-notification");
        getNotification.setOnAction(event -> showcaseNotifications.add(new Notification(
                new NotificationId(UUID.randomUUID()), SHOWCASE_ACCOUNT_ID,
                NotificationType.INCIDENT, Optional.empty(),
                "A new incident was submitted.", Instant.now(), 1)));
        return UiComponents.panel("Notification center", getNotification, notificationCenter);
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

    enum DemoRole {
        REPORTER("Reporter view", IncidentTableConfiguration.reporter()),
        RESPONDER("Responder view", IncidentTableConfiguration.responder()),
        ADMINISTRATOR("Administrator view", IncidentTableConfiguration.administrator());

        private final String label;
        private final IncidentTableConfiguration configuration;

        DemoRole(String label, IncidentTableConfiguration configuration) {
            this.label = label;
            this.configuration = configuration;
        }
    }

    /** In-memory showcase store containing authorized, privacy-safe dummy rows. */
    static final class DemoIncidentStore {
        private static final AccountId ALEX = accountId("aaaaaaaa-0000-0000-0000-000000000001");
        private static final AccountId JAMIE = accountId("aaaaaaaa-0000-0000-0000-000000000002");
        private static final AccountId TAYLOR = accountId("aaaaaaaa-0000-0000-0000-000000000003");
        private static final AccountId MORGAN = accountId("bbbbbbbb-0000-0000-0000-000000000001");
        private static final AccountId PRIYA = accountId("bbbbbbbb-0000-0000-0000-000000000002");
        private static final IncidentActionModel NO_ACTIONS = new IncidentActionModel(
                false, false, false, false, false, false, false, false, false);
        private final List<DemoIncident> incidents = List.of(
                incident("10000000-0000-0000-0000-000000000001", "Printer unavailable", "Printer on level three is offline",
                        IncidentCategory.IT, IncidentStatus.SUBMITTED, ALEX, "Alex Rivera", null, "Unassigned",
                        "2026-09-23T01:40:00Z", new SloSummaryModel("2h remaining", .55, false),
                        IncidentSloState.WITHIN_TARGET, false),
                incident("20000000-0000-0000-0000-000000000002", "Water leak", "Leak beside the pantry",
                        IncidentCategory.FACILITIES, IncidentStatus.ASSIGNED, ALEX, "Anonymous reporter", MORGAN, "Morgan Lee",
                        "2026-09-23T00:15:00Z", new SloSummaryModel("Overdue 18m", 1.12, true),
                        IncidentSloState.OVERDUE, true),
                incident("30000000-0000-0000-0000-000000000003", "Access request", "Card access is not working",
                        IncidentCategory.HUMAN_RELATIONS, IncidentStatus.RESOLVED, JAMIE, "Jamie Chen", PRIYA, "Priya Shah",
                        "2026-09-22T09:30:00Z", new SloSummaryModel("Met", .72, false),
                        IncidentSloState.WITHIN_TARGET, false),
                incident("40000000-0000-0000-0000-000000000004", "VPN disconnects", "Remote connection drops repeatedly",
                        IncidentCategory.IT, IncidentStatus.ASSIGNED, JAMIE, "Jamie Chen", MORGAN, "Morgan Lee",
                        "2026-09-21T06:05:00Z", new SloSummaryModel("At risk", .88, false),
                        IncidentSloState.WITHIN_TARGET, false),
                incident("50000000-0000-0000-0000-000000000005", "Email delivery delayed", "Outbound messages remain queued",
                        IncidentCategory.IT, IncidentStatus.SUBMITTED, TAYLOR, "Taylor Wong", null, "Unassigned",
                        "2026-09-20T03:25:00Z", new SloSummaryModel("4h remaining", .35, false),
                        IncidentSloState.WITHIN_TARGET, false),
                incident("60000000-0000-0000-0000-000000000006", "Shared drive unavailable", "Department share cannot be opened",
                        IncidentCategory.IT, IncidentStatus.SUBMITTED, TAYLOR, "Anonymous reporter", null, "Unassigned",
                        "2026-09-19T08:10:00Z", new SloSummaryModel("Overdue 1h", 1.25, true),
                        IncidentSloState.OVERDUE, true));

        List<IncidentRowModel> query(DemoRole role, IncidentSearchCriteria criteria) {
            Stream<DemoIncident> stream = incidents.stream().filter(incident -> visibleTo(role, incident));
            if (!criteria.text().isBlank()) {
                String term = criteria.text().toLowerCase(Locale.ROOT);
                stream = stream.filter(incident -> incident.searchText().contains(term));
            }
            if (!criteria.categories().isEmpty()) {
                stream = stream.filter(incident -> criteria.categories().contains(incident.category));
            }
            if (!criteria.statuses().isEmpty()) {
                stream = stream.filter(incident -> criteria.statuses().contains(incident.status));
            }
            if (criteria.assignmentState() != AssignmentState.ANY) {
                boolean assigned = criteria.assignmentState() == AssignmentState.ASSIGNED;
                stream = stream.filter(incident -> assigned == !incident.row.assigneeLabel().equals("Unassigned"));
            }
            if (criteria.reporterId().isPresent()) {
                AccountId reporterId = criteria.reporterId().orElseThrow();
                stream = stream.filter(incident -> !incident.row.anonymous() && incident.reporterId.equals(reporterId));
            }
            if (criteria.responderId().isPresent()) {
                AccountId responderId = criteria.responderId().orElseThrow();
                stream = stream.filter(incident -> incident.responderId.filter(responderId::equals).isPresent());
            }
            if (criteria.createdFrom().isPresent()) {
                Instant createdFrom = criteria.createdFrom().orElseThrow();
                stream = stream.filter(incident -> !incident.createdAt.isBefore(createdFrom));
            }
            if (criteria.createdThrough().isPresent()) {
                Instant createdThrough = criteria.createdThrough().orElseThrow();
                stream = stream.filter(incident -> !incident.createdAt.isAfter(createdThrough));
            }
            if (!criteria.sloStates().isEmpty()) {
                stream = stream.filter(incident -> criteria.sloStates().contains(incident.sloState));
            }
            Comparator<DemoIncident> comparator = comparator(criteria);
            if (criteria.sort().direction() == SortDirection.DESCENDING) {
                comparator = comparator.reversed();
            }
            return stream.sorted(comparator.thenComparing(incident -> incident.row.id().value()))
                    .map(DemoIncident::row).toList();
        }

        private boolean visibleTo(DemoRole role, DemoIncident incident) {
            return switch (role) {
                case REPORTER -> incident.reporterId.equals(ALEX);
                case RESPONDER -> incident.category == IncidentCategory.IT;
                case ADMINISTRATOR -> true;
            };
        }

        private Comparator<DemoIncident> comparator(IncidentSearchCriteria criteria) {
            return switch (criteria.sort().field()) {
                case TITLE -> Comparator.comparing(incident -> incident.row.title(), String.CASE_INSENSITIVE_ORDER);
                case STATUS -> Comparator.comparing(incident -> incident.row.statusLabel());
                case CATEGORY -> Comparator.comparing(incident -> incident.row.categoryLabel());
                case QUEUE_ENTERED_AT -> Comparator.comparing(incident -> incident.row.queueEnteredAt());
                case CREATED_AT, SUBMITTED_AT -> Comparator.comparing(incident -> incident.row.createdAt());
            };
        }

        List<IncidentFilterBar.AccountOption> reporters() {
            return List.of(
                    new IncidentFilterBar.AccountOption(ALEX, "Alex Rivera"),
                    new IncidentFilterBar.AccountOption(JAMIE, "Jamie Chen"),
                    new IncidentFilterBar.AccountOption(TAYLOR, "Taylor Wong"));
        }

        List<IncidentFilterBar.AccountOption> responders() {
            return List.of(
                    new IncidentFilterBar.AccountOption(MORGAN, "Morgan Lee"),
                    new IncidentFilterBar.AccountOption(PRIYA, "Priya Shah"));
        }

        private static AccountId accountId(String id) {
            return new AccountId(UUID.fromString(id));
        }

        private static DemoIncident incident(
                String id, String title, String description, IncidentCategory category,
                IncidentStatus status, AccountId reporterId, String reporter, AccountId responderId,
                String assignee, String createdAt, SloSummaryModel slo,
                IncidentSloState sloState, boolean anonymous) {
            Instant createdAtInstant = Instant.parse(createdAt);
            String createdAtLabel = UiComponents.localDateTimeFormatter().format(createdAtInstant);
            IncidentRowModel row = new IncidentRowModel(
                    new IncidentId(UUID.fromString(id)), title,
                    IncidentDisplayLabels.category(category), IncidentDisplayLabels.status(status),
                    reporter, assignee, createdAtLabel, createdAtLabel, slo, anonymous, 0, NO_ACTIONS);
            return new DemoIncident(
                    row, description.toLowerCase(Locale.ROOT), category, status,
                    reporterId, Optional.ofNullable(responderId), createdAtInstant, sloState);
        }

        private record DemoIncident(
                IncidentRowModel row, String description, IncidentCategory category,
                IncidentStatus status, AccountId reporterId, Optional<AccountId> responderId,
                Instant createdAt, IncidentSloState sloState) {
            private String searchText() {
                return (row.id().value() + " " + row.title() + " " + description).toLowerCase(Locale.ROOT);
            }
        }
    }
}

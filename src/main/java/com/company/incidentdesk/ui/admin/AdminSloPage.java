package com.company.incidentdesk.ui.admin;

import java.util.Objects;
import java.util.function.Function;

import com.company.incidentdesk.application.slo.SloConfigurationGateway;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.UiComponents;
import com.company.incidentdesk.ui.shared.components.ValidatedField;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Administrator interface for versioned, per-category SLO targets. */
public final class AdminSloPage extends BorderPane {
    private final AdminSloPresenter presenter;
    private final VBox body = new VBox(16);
    private final ComboBox<IncidentCategory> category = new ComboBox<>();
    private final TextField claimMinutes = new TextField();
    private final TextField progressMinutes = new TextField();
    private final TextField reopenPercent = new TextField();
    private final Button save = UiComponents.action("Save new target version", ActionStyle.PRIMARY);
    private final ValidatedField claimField = UiComponents.field(
            "Average time to claim (minutes)", claimMinutes);
    private final ValidatedField progressField = UiComponents.field(
            "Average time in progress (minutes)", progressMinutes);
    private final ValidatedField reopenField = UiComponents.field("Average reopen rate (%)", reopenPercent);

    public AdminSloPage(SloConfigurationGateway configurations) {
        presenter = new AdminSloPresenter(Objects.requireNonNull(configurations, "configurations"));
        configureCategorySelector();
        configureSaveAction();
        Label title = new Label("SLO configuration");
        title.getStyleClass().add("page-title");
        Label explanation = new Label(
                "Targets use elapsed wall-clock time. New versions apply prospectively and do not rewrite historical compliance.");
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
                "Loading SLO configuration", "Reading versioned category targets.", FeedbackType.LOADING));
        case UNAVAILABLE -> body.getChildren().add(feedback(
                "SLO configuration unavailable", "Sign in as an administrator and try again.", FeedbackType.ERROR));
        case STORAGE_ERROR -> body.getChildren().addAll(feedback(
                "SLO configuration could not be stored",
                "No target was changed. Check application storage and try again.", FeedbackType.ERROR), retryButton());
        case READY, SAVED, VALIDATION_ERROR -> renderReady();
        }
    }

    private void renderReady() {
        if (presenter.state() == AdminSloPresenter.State.SAVED) {
            body.getChildren().add(feedback(
                    "SLO targets saved", "A new audited target version is now effective.", FeedbackType.SUCCESS));
        } else if (presenter.state() == AdminSloPresenter.State.VALIDATION_ERROR) {
            showValidationErrors();
            body.getChildren().add(feedback(
                    "Check the target values",
                    "Durations must be non-negative whole minutes and reopen rate must be from 0 to 100.",
                    FeedbackType.ERROR));
        }
        body.getChildren().addAll(categorySelector(), currentTarget(), editor(), historyPanel());
    }

    private Node categorySelector() {
        if (category.getValue() != presenter.selectedCategory()) {
            category.setValue(presenter.selectedCategory());
        }
        return UiComponents.panel("Incident category", UiComponents.field("Category", category));
    }

    private Node currentTarget() {
        SloTargetVersion version = presenter.current(presenter.selectedCategory()).orElse(null);
        Label effectiveFrom = new Label(version == null
                ? "No effective target version yet"
                : "Effective from " + UiComponents.localDateTimeFormatter().format(version.effectiveFrom()));
        effectiveFrom.getStyleClass().add("muted");

        return UiComponents.panel(
                "Current effective target — " + presenter.selectedCategory().displayName(),
                effectiveFrom,
                SloTargetMetrics.create(version));
    }

    private Node editor() {
        populateInputs();
        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(14);
        form.add(claimField, 0, 0);
        form.add(progressField, 1, 0);
        form.add(reopenField, 2, 0);
        updateSaveAvailability();
        return UiComponents.panel("Configure category targets", form, save);
    }

    private void configureSaveAction() {
        save.setAccessibleText("Save new SLO target version for selected category");
        save.setOnAction(event -> {
            clearValidationErrors();
            presenter.save(claimMinutes.getText(), progressMinutes.getText(), reopenPercent.getText());
            render();
        });
        claimMinutes.textProperty().addListener((observable, previous, current) -> updateSaveAvailability());
        progressMinutes.textProperty().addListener((observable, previous, current) -> updateSaveAvailability());
        reopenPercent.textProperty().addListener((observable, previous, current) -> updateSaveAvailability());
    }

    private void updateSaveAvailability() {
        save.setDisable(!presenter.hasChanges(
                claimMinutes.getText(), progressMinutes.getText(), reopenPercent.getText()));
    }

    private void configureCategorySelector() {
        category.setItems(FXCollections.observableArrayList(IncidentCategory.values()));
        category.setConverter(new StringConverter<>() {
            @Override
            public String toString(IncidentCategory value) {
                return value == null ? "" : value.displayName();
            }

            @Override
            public IncidentCategory fromString(String value) {
                throw new UnsupportedOperationException("Category selection is read-only");
            }
        });
        category.setAccessibleText("Incident category");
        category.setOnAction(event -> {
            if (category.getValue() == null || category.getValue() == presenter.selectedCategory()) {
                return;
            }
            clearValidationErrors();
            presenter.selectCategory(category.getValue());
            populateInputs();
            render();
        });
    }

    private Node historyPanel() {
        TableView<SloTargetVersion> table = new TableView<>();
        table.setAccessibleText("Read-only SLO configuration history for "
                + presenter.selectedCategory().displayName());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("Effective from", version ->
                UiComponents.localDateTimeFormatter().format(version.effectiveFrom())));
        table.getColumns().add(column("Time to claim", version ->
                SloTargetFormat.duration(version.target().timeToClaimTarget())));
        table.getColumns().add(column("Time in progress", version ->
                SloTargetFormat.duration(version.target().timeInProgressTarget())));
        table.getColumns().add(column("Reopen rate", version ->
                SloTargetFormat.percent(version.target().reopenRateTarget())));
        table.getColumns().add(column("Version", version -> version.id().value().toString()));
        table.getItems().setAll(presenter.history());
        table.setPlaceholder(new Label("No configuration history for this category"));
        return UiComponents.panel("Configuration history — "
                + presenter.selectedCategory().displayName(), table);
    }

    private TableColumn<SloTargetVersion, String> column(
            String title, Function<SloTargetVersion, String> value) {
        TableColumn<SloTargetVersion, String> tableColumn = new TableColumn<>(title);
        tableColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return tableColumn;
    }

    private void populateInputs() {
        presenter.current(presenter.selectedCategory()).ifPresentOrElse(version -> {
            claimMinutes.setText(Long.toString(version.target().timeToClaimTarget().toMinutes()));
            progressMinutes.setText(Long.toString(version.target().timeInProgressTarget().toMinutes()));
            reopenPercent.setText(SloTargetFormat.percentValue(version.target().reopenRateTarget()));
        }, () -> {
            claimMinutes.clear();
            progressMinutes.clear();
            reopenPercent.clear();
        });
    }

    private void showValidationErrors() {
        claimField.showError("Enter a non-negative whole number of minutes.");
        progressField.showError("Enter a non-negative whole number of minutes.");
        reopenField.showError("Enter a percentage from 0 to 100.");
    }

    private void clearValidationErrors() {
        claimField.clearError();
        progressField.clearError();
        reopenField.clearError();
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

package com.company.incidentdesk.ui.responder;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.presentation.ResponderDashboardModel;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.IncidentTable;
import com.company.incidentdesk.ui.shared.components.IncidentTableColumn;
import com.company.incidentdesk.ui.shared.components.IncidentTableConfiguration;
import com.company.incidentdesk.ui.shared.components.IncidentTableState;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Responder queues; the authenticated shell supplies services and detail navigation. */
public final class ResponderPage extends BorderPane {
    private final ResponderDashboardPresenter presenter;
    private final Supplier<ApplicationResult<ResponderDashboardModel>> load;
    private final Consumer<IncidentId> onOpenDetail;
    private final IncidentTable eligible = createQueueTable("Eligible incidents");
    private final IncidentTable assigned = createQueueTable("My assigned incidents");
    private final Button refresh = UiComponents.action("Refresh", ActionStyle.SECONDARY);
    private final Button open = UiComponents.action("Open details", ActionStyle.PRIMARY);
    private final VBox feedback = new VBox();
    private Task<ApplicationResult<ResponderDashboardModel>> activeLoad;
    private boolean rendering;

    public ResponderPage(IncidentService service, IncidentPresentationMapper mapper,
            SessionProvider sessions, Consumer<IncidentId> onOpenDetail) {
        this(sessions, () -> service.responderDashboard(mapper), onOpenDetail);
    }

    ResponderPage(SessionProvider sessions, Supplier<ApplicationResult<ResponderDashboardModel>> load,
            Consumer<IncidentId> onOpenDetail) {
        presenter = new ResponderDashboardPresenter(sessions);
        this.load = Objects.requireNonNull(load, "load");
        this.onOpenDetail = Objects.requireNonNull(onOpenDetail, "onOpenDetail");
        setCenter(createContent());
        configureSelection(eligible, assigned);
        configureSelection(assigned, eligible);
        sceneProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                deactivate();
            } else {
                refresh();
            }
        });
        render();
    }

    private ScrollPane createContent() {
        Label title = new Label("Responder");
        title.getStyleClass().add("page-title");
        Label description = new Label("Review eligible incidents and manage incidents assigned to you.");
        description.setWrapText(true);
        refresh.setOnAction(event -> refresh());
        open.setOnAction(event -> openSelected());
        FlowPane actions = new FlowPane(12, 12);
        actions.getChildren().addAll(refresh, open);
        VBox content = new VBox(16, title, description, actions, feedback,
                UiComponents.panel("Eligible queue", eligible),
                UiComponents.panel("My assigned incidents", assigned));
        content.setPadding(new Insets(24));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        return scroll;
    }

    /** Rechecks session/category access, discarding old content while local files are read. */
    public void refresh() {
        cancelLoad();
        long request = presenter.beginRefresh();
        render();
        if (presenter.state() != ResponderDashboardPresenter.State.LOADING) {
            return;
        }
        Task<ApplicationResult<ResponderDashboardModel>> task = new Task<>() {
            @Override protected ApplicationResult<ResponderDashboardModel> call() { return load.get(); }
        };
        activeLoad = task;
        task.setOnSucceeded(event -> finishRefresh(request, task.getValue()));
        task.setOnFailed(event -> finishRefresh(request, unavailable()));
        Thread.startVirtualThread(task);
    }

    private void finishRefresh(long request, ApplicationResult<ResponderDashboardModel> result) {
        presenter.completeRefresh(request, result);
        render();
    }

    private void configureSelection(IncidentTable table, IncidentTable other) {
        table.selectedRowProperty().addListener((observable, oldRow, row) -> {
            if (rendering) {
                return;
            }
            if (row == null) {
                if (other.selectedRowProperty().get() == null) {
                    presenter.select(null);
                    open.setDisable(true);
                }
                return;
            }
            other.clearSelection();
            presenter.select(row.id());
            if (presenter.state() == ResponderDashboardPresenter.State.UNAVAILABLE) {
                render();
            } else {
                open.setDisable(presenter.selectedIncident().isEmpty());
            }
        });
        table.setOnOpenDetail(row -> {
            presenter.select(row.id());
            openSelected();
        });
        table.setOnRefresh(ignored -> refresh());
    }

    private void openSelected() {
        Optional<IncidentId> selected = presenter.selectedIncident();
        render();
        // Detail navigation receives only an ID; the detail service must authorize it again.
        selected.ifPresent(onOpenDetail);
    }

    private void render() {
        rendering = true;
        try {
            Optional<IncidentId> selected = presenter.state() == ResponderDashboardPresenter.State.READY
                    ? presenter.selectedIncident() : Optional.empty();
            renderTable(eligible, presenter.model().eligible());
            renderTable(assigned, presenter.model().assigned());
            selected.ifPresent(id -> {
                eligible.selectIncident(id);
                assigned.selectIncident(id);
            });
            open.setDisable(selected.isEmpty());
            refresh.setDisable(presenter.state() == ResponderDashboardPresenter.State.LOADING);
            renderFeedback();
        } finally {
            rendering = false;
        }
    }

    private void renderFeedback() {
        feedback.getChildren().clear();
        switch (presenter.state()) {
        case LOADING -> feedback.getChildren().add(UiComponents.feedback(
                "Loading incidents", "Reading your current queues.", FeedbackType.LOADING));
        case UNAVAILABLE -> feedback.getChildren().add(UiComponents.feedback(
                "Incidents unavailable", "Sign in as a responder and refresh to try again.", FeedbackType.ERROR));
        case READY -> { }
        }
        feedback.setManaged(!feedback.getChildren().isEmpty());
        feedback.setVisible(feedback.isManaged());
    }

    private void renderTable(IncidentTable table, List<IncidentRowModel> rows) {
        table.clearSelection();
        table.setState(switch (presenter.state()) {
            case LOADING -> IncidentTableState.loading();
            case READY -> IncidentTableState.loaded(rows);
            case UNAVAILABLE -> IncidentTableState.error("Sign in as a responder and refresh to try again.");
        });
    }

    private static IncidentTable createQueueTable(String accessibleName) {
        IncidentTable table = new IncidentTable(new IncidentTableConfiguration(List.of(
                IncidentTableColumn.TITLE, IncidentTableColumn.CATEGORY, IncidentTableColumn.STATUS,
                IncidentTableColumn.REPORTER, IncidentTableColumn.QUEUE_ENTERED), false, false));
        table.setTableAccessibleText(accessibleName);
        table.setFiltersVisible(false);
        table.setColumnSortingEnabled(false);
        table.setPrefHeight(240);
        table.setMinHeight(180);
        return table;
    }

    private void deactivate() {
        cancelLoad();
        presenter.clear();
        render();
    }

    private void cancelLoad() {
        if (activeLoad != null) {
            activeLoad.cancel();
            activeLoad = null;
        }
    }

    private static ApplicationResult<ResponderDashboardModel> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }
}

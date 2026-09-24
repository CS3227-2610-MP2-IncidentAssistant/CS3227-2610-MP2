package com.company.incidentdesk.ui.admin;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.presentation.IncidentRowModel;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.application.slo.SloConfigurationService;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.ui.shared.components.IncidentTable;
import com.company.incidentdesk.ui.shared.components.IncidentTableConfiguration;
import com.company.incidentdesk.ui.shared.components.IncidentTableState;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Service-backed administrator view of company incidents. */
public final class AdminIncidentPage extends BorderPane {
    private final AdminDashboardPresenter presenter;
    private final Supplier<ApplicationResult<List<IncidentRowModel>>> load;
    private final IncidentTable incidents = new IncidentTable(IncidentTableConfiguration.administrator());
    private Task<ApplicationResult<List<IncidentRowModel>>> activeLoad;

    public AdminIncidentPage(
            IncidentService service,
            IncidentPresentationMapper mapper,
            SessionProvider sessions,
            SloConfigurationService sloConfigurations,
            Consumer<IncidentId> onOpenDetail) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(onOpenDetail, "onOpenDetail");
        presenter = new AdminDashboardPresenter(sessions);
        load = () -> service.administratorIncidents(incidents.criteria(), mapper);
        incidents.setTableAccessibleText("Company incidents");
        incidents.setOnRefresh(criteria -> refresh());
        incidents.setOnOpenDetail(row -> onOpenDetail.accept(row.id()));
        setCenter(content(Objects.requireNonNull(sloConfigurations, "sloConfigurations")));
        sceneProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                deactivate();
            } else {
                refresh();
            }
        });
        render();
    }

    private ScrollPane content(SloConfigurationService sloConfigurations) {
        Label title = new Label("Administrator dashboard");
        title.getStyleClass().add("page-title");
        Label description = new Label("Monitor SLO targets and incidents across the company.");
        description.setWrapText(true);
        VBox sloOverview = sloOverview(sloConfigurations);
        VBox content = new VBox(16, title, description, sloOverview,
                UiComponents.panel("Incidents", incidents));
        content.setPadding(new Insets(24));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private VBox sloOverview(SloConfigurationService service) {
        var result = service.currentTargets();
        if (!result.isSuccess() || result.value().orElseThrow().isEmpty()) {
            return UiComponents.panel("SLO overview", UiComponents.feedback(
                    "No SLO summary available",
                    "Use SLO configuration to define category targets.", FeedbackType.EMPTY));
        }
        FlowPane cards = new FlowPane(12, 12);
        result.value().orElseThrow().forEach(version -> cards.getChildren().add(UiComponents.metricCard(
                version.category().name(), "Configured",
                "Claim " + version.target().timeToClaimTarget().toHours() + "h · In progress "
                        + version.target().timeInProgressTarget().toHours() + "h")));
        return UiComponents.panel("SLO overview", cards);
    }

    private void refresh() {
        cancelLoad();
        long request = presenter.beginRefresh();
        render();
        if (presenter.state() != AdminDashboardPresenter.State.LOADING) {
            return;
        }
        Task<ApplicationResult<List<IncidentRowModel>>> task = new Task<>() {
            @Override protected ApplicationResult<List<IncidentRowModel>> call() { return load.get(); }
        };
        activeLoad = task;
        task.setOnSucceeded(event -> finishRefresh(request, task.getValue()));
        task.setOnFailed(event -> finishRefresh(request, unavailable()));
        Thread.startVirtualThread(task);
    }

    private void finishRefresh(long request, ApplicationResult<List<IncidentRowModel>> result) {
        presenter.completeRefresh(request, result);
        render();
    }

    private void render() {
        incidents.setState(switch (presenter.state()) {
            case LOADING -> IncidentTableState.loading();
            case READY -> IncidentTableState.loaded(presenter.rows());
            case UNAVAILABLE -> IncidentTableState.error(
                    "Sign in as an administrator and refresh to try again.");
        });
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

    private static ApplicationResult<List<IncidentRowModel>> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }
}

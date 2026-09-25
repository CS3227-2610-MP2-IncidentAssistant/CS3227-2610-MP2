package com.company.incidentdesk.ui.admin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.presentation.AdministratorIncidentListModel;
import com.company.incidentdesk.application.presentation.IncidentIdentityOptionModel;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.result.ApplicationError;
import com.company.incidentdesk.application.result.ApplicationErrorCode;
import com.company.incidentdesk.application.result.ApplicationResult;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.application.slo.SloConfigurationService;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.domain.incident.IncidentCategory;
import com.company.incidentdesk.domain.slo.SloTargetVersion;
import com.company.incidentdesk.ui.shared.components.IncidentTable;
import com.company.incidentdesk.ui.shared.components.IncidentTableConfiguration;
import com.company.incidentdesk.ui.shared.components.IncidentTableState;
import com.company.incidentdesk.ui.shared.components.IncidentFilterBar.AccountOption;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Service-backed administrator view of company incidents. */
public final class AdminIncidentPage extends BorderPane {
    private final AdminDashboardPresenter presenter;
    private final IncidentService service;
    private final IncidentPresentationMapper mapper;
    private final IncidentTable incidents = new IncidentTable(IncidentTableConfiguration.administrator());
    private Task<ApplicationResult<AdministratorIncidentListModel>> activeLoad;

    public AdminIncidentPage(
            IncidentService service,
            IncidentPresentationMapper mapper,
            SessionProvider sessions,
            SloConfigurationService sloConfigurations,
            Consumer<IncidentId> onOpenDetail) {
        this.service = Objects.requireNonNull(service, "service");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(onOpenDetail, "onOpenDetail");
        presenter = new AdminDashboardPresenter(sessions);
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
        if (!result.isSuccess()) {
            return UiComponents.panel("SLO overview", UiComponents.feedback(
                    "SLO summary unavailable",
                    "Sign in as an administrator and try again.", FeedbackType.ERROR));
        }
        Map<IncidentCategory, SloTargetVersion> current = new EnumMap<>(IncidentCategory.class);
        result.value().orElseThrow().forEach(version -> current.put(version.category(), version));
        VBox categories = new VBox(14);
        for (IncidentCategory category : IncidentCategory.values()) {
            categories.getChildren().add(categoryMetrics(category, current.get(category)));
        }
        return UiComponents.panel("SLO overview", categories);
    }

    private VBox categoryMetrics(IncidentCategory category, SloTargetVersion version) {
        return UiComponents.panel(category.displayName(), SloTargetMetrics.create(version));
    }

    private void refresh() {
        cancelLoad();
        var criteria = incidents.criteria();
        long request = presenter.beginRefresh();
        render();
        if (presenter.state() != AdminDashboardPresenter.State.LOADING) {
            return;
        }
        Task<ApplicationResult<AdministratorIncidentListModel>> task = new Task<>() {
            @Override protected ApplicationResult<AdministratorIncidentListModel> call() {
                return service.administratorIncidentList(criteria, mapper);
            }
        };
        activeLoad = task;
        task.setOnSucceeded(event -> finishRefresh(request, task.getValue()));
        task.setOnFailed(event -> finishRefresh(request, unavailable()));
        Thread.startVirtualThread(task);
    }

    private void finishRefresh(long request, ApplicationResult<AdministratorIncidentListModel> result) {
        presenter.completeRefresh(request, result);
        render();
    }

    private void render() {
        if (presenter.state() == AdminDashboardPresenter.State.READY) {
            incidents.setIdentityOptions(options(presenter.reporters()), options(presenter.responders()));
        } else if (presenter.state() == AdminDashboardPresenter.State.UNAVAILABLE) {
            incidents.setIdentityOptions(List.of(), List.of());
        }
        incidents.setState(switch (presenter.state()) {
            case LOADING -> IncidentTableState.loading();
            case READY -> IncidentTableState.loaded(presenter.rows());
            case UNAVAILABLE -> IncidentTableState.error(
                    "Sign in as an administrator and refresh to try again.");
        });
    }

    private static List<AccountOption> options(List<IncidentIdentityOptionModel> values) {
        return values.stream().map(value -> new AccountOption(value.id(), value.displayName())).toList();
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

    private static ApplicationResult<AdministratorIncidentListModel> unavailable() {
        return ApplicationResult.failure(ApplicationError.of(ApplicationErrorCode.RESOURCE_UNAVAILABLE));
    }
}

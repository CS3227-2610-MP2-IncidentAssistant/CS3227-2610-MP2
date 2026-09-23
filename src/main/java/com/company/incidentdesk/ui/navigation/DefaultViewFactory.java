package com.company.incidentdesk.ui.navigation;

import java.util.Objects;
import java.util.function.Consumer;

import com.company.incidentdesk.application.incident.IncidentService;
import com.company.incidentdesk.application.presentation.IncidentPresentationMapper;
import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.incident.IncidentId;
import com.company.incidentdesk.ui.admin.AdminPage;
import com.company.incidentdesk.ui.reporter.ReporterPage;
import com.company.incidentdesk.ui.responder.ResponderPage;
import com.company.incidentdesk.ui.shared.components.FeedbackType;
import com.company.incidentdesk.ui.shared.components.ActionStyle;
import com.company.incidentdesk.ui.shared.components.UiComponents;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

/** Default role-aware view factory backed by the shared application services. */
public final class DefaultViewFactory implements ViewFactory {
    private final IncidentService incidents;
    private final IncidentPresentationMapper mapper;
    private final SessionProvider sessions;

    public DefaultViewFactory(
            IncidentService incidents,
            IncidentPresentationMapper mapper,
            SessionProvider sessions) {
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @Override
    public Node createDashboard(Account account, Consumer<IncidentId> onOpenIncident) {
        return switch (account.role()) {
        case REPORTER -> new ReporterPage();
        case RESPONDER -> new ResponderPage(incidents, mapper, sessions, onOpenIncident);
        case ADMINISTRATOR -> new AdminPage();
        };
    }

    @Override
    public Node createIncidentDetail(Account account, IncidentId incidentId, Runnable onBack) {
        Button back = UiComponents.action("Back to dashboard", ActionStyle.SECONDARY);
        back.setOnAction(event -> onBack.run());
        VBox detail = new VBox(16, UiComponents.feedback(
                "Incident details are not available yet",
                "Return to the dashboard while the shared detail workflow is completed.",
                FeedbackType.EMPTY), back);
        detail.setAccessibleText("Incident detail for " + incidentId.value());
        return detail;
    }
}

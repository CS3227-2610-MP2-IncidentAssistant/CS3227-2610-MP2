package com.company.incidentdesk.ui.navigation;

import java.util.function.Consumer;

import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.incident.IncidentId;

import javafx.scene.Node;

/** Creates application views from centrally assembled services. */
public interface ViewFactory {
    Node createDashboard(Account account, Consumer<IncidentId> onOpenIncident);

    Node createIncidentDetail(Account account, IncidentId incidentId, Runnable onBack);
}

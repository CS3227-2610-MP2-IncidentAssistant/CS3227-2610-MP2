package com.company.incidentdesk.startup;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import com.company.incidentdesk.domain.account.Role;
import com.company.incidentdesk.ui.admin.AdminPage;
import com.company.incidentdesk.ui.reporter.ReporterPage;
import com.company.incidentdesk.ui.responder.ResponderPage;
import com.company.incidentdesk.ui.shared.components.ComponentShowcasePage;
import com.company.incidentdesk.ui.shared.components.RoleSelectionPage;
import com.company.incidentdesk.ui.shared.theme.ApplicationTheme;

/** JavaFX application for Incident Desk. */
public final class IncidentDeskApplication extends Application {
    private static final double INITIAL_WIDTH = 720;
    private static final double INITIAL_HEIGHT = 480;

    private Scene scene;

    @Override
    public void start(Stage primaryStage) {
        scene = new Scene(createRoleSelectionPage(), INITIAL_WIDTH, INITIAL_HEIGHT);
        ApplicationTheme.applyTo(scene);
        primaryStage.setTitle("Incident Desk");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private Parent createRoleSelectionPage() {
        return new RoleSelectionPage(this::showRolePage, this::showComponentShowcase);
    }

    private void showRolePage(Role role) {
        Parent rolePage = switch (role) {
            case REPORTER -> new ReporterPage(this::showRoleSelectionPage);
            case RESPONDER -> new ResponderPage(this::showRoleSelectionPage);
            case ADMINISTRATOR -> new AdminPage(this::showRoleSelectionPage);
        };
        scene.setRoot(rolePage);
    }

    private void showRoleSelectionPage() {
        scene.setRoot(createRoleSelectionPage());
    }

    private void showComponentShowcase() {
        scene.setRoot(new ComponentShowcasePage(this::showRoleSelectionPage));
    }
}

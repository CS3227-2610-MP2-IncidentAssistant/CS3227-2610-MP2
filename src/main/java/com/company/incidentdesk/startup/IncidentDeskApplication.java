package com.company.incidentdesk.startup;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import com.company.incidentdesk.ui.navigation.ApplicationNavigator;
import com.company.incidentdesk.ui.navigation.DefaultViewFactory;
import com.company.incidentdesk.ui.shared.theme.ApplicationTheme;

/** JavaFX application for Incident Desk. */
public final class IncidentDeskApplication extends Application {
    static final double INITIAL_WINDOW_WIDTH = 1600;
    static final double INITIAL_WINDOW_HEIGHT = 900;

    private Scene scene;
    private ApplicationContext applicationContext;
    private ApplicationNavigator navigator;

    @Override
    public void start(Stage primaryStage) {
        applicationContext = ApplicationContext.openDefault();
        scene = new Scene(new StackPane());
        ApplicationTheme.applyTo(scene);
        navigator = new ApplicationNavigator(
                scene,
                applicationContext.sessions(),
                applicationContext.notifications(),
                new DefaultViewFactory(
                        applicationContext.incidents(),
                        applicationContext.presentationMapper(),
                        applicationContext.sessions(),
                        applicationContext.accountDirectory(),
                        applicationContext.accountDeletion(),
                        applicationContext.passwordResets(),
                        applicationContext.responderAccess(),
                        applicationContext.promotionRequests(),
                        applicationContext.auditLog(),
                        applicationContext.sloConfigurations(),
                        applicationContext.incidentDetails(),
                        applicationContext.comments(),
                        applicationContext.attachments(),
                        applicationContext.statistics()),
                applicationContext.registrations(),
                applicationContext.passwords());
        navigator.start();
        primaryStage.setTitle("Incident Desk");
        primaryStage.setScene(scene);
        setInitialWindowSize(primaryStage);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (navigator != null) {
            navigator.close();
        }
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    static void setInitialWindowSize(Stage stage) {
        stage.setWidth(INITIAL_WINDOW_WIDTH);
        stage.setHeight(INITIAL_WINDOW_HEIGHT);
    }

}
